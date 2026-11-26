use reqwest::blocking::Client;
use serde::Serialize;
use std::{
    env,
    fs::{self, OpenOptions},
    io::{self, Write},
    net::TcpListener,
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    thread::{self, JoinHandle},
    time::{Duration, Instant},
};
use tauri::{AppHandle, Emitter, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tungstenite::{client::IntoClientRequest, connect, stream::MaybeTlsStream, Message};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

const API_BASE: &str = "http://127.0.0.1:8080";
const HEALTH_TIMEOUT: Duration = Duration::from_secs(120);

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct BackendStatus {
    phase: String,
    message: String,
    diagnostics_path: Option<String>,
}

impl BackendStatus {
    fn new(phase: &str, message: impl Into<String>, diagnostics_path: Option<String>) -> Self {
        Self {
            phase: phase.into(),
            message: message.into(),
            diagnostics_path,
        }
    }
}

struct BackendSupervisor {
    status: Mutex<BackendStatus>,
    child: Mutex<Option<Child>>,
    worker: Mutex<Option<JoinHandle<()>>>,
    stop: AtomicBool,
    data_dir: PathBuf,
    log_path: PathBuf,
}

impl BackendSupervisor {
    fn new() -> io::Result<Self> {
        let data_dir = app_data_dir()?;
        let log_path = data_dir.join("backend.log");
        Ok(Self {
            status: Mutex::new(BackendStatus::new(
                "starting",
                "Starting the local API…",
                Some(log_path.to_string_lossy().into_owned()),
            )),
            child: Mutex::new(None),
            worker: Mutex::new(None),
            stop: AtomicBool::new(false),
            data_dir,
            log_path,
        })
    }

    fn set_status(&self, app: &AppHandle, status: BackendStatus) {
        *self.status.lock().expect("backend status mutex poisoned") = status.clone();
        let _ = app.emit("backend-status", status);
    }

    fn status(&self) -> BackendStatus {
        self.status
            .lock()
            .expect("backend status mutex poisoned")
            .clone()
    }

    fn start(self: &Arc<Self>, app: AppHandle) {
        self.stop.store(false, Ordering::SeqCst);
        let supervisor = Arc::clone(self);
        let worker = thread::spawn(move || supervisor.run(app));
        *self.worker.lock().expect("backend worker mutex poisoned") = Some(worker);
    }

    fn run(&self, app: AppHandle) {
        self.set_status(
            &app,
            BackendStatus::new(
                "starting",
                "Checking the local API port…",
                Some(self.log_path.to_string_lossy().into_owned()),
            ),
        );
        if let Err(error) = fs::create_dir_all(&self.data_dir) {
            self.set_status(
                &app,
                BackendStatus::new(
                    "failed",
                    format!("Could not access the FocusDesktop data directory: {error}"),
                    Some(self.log_path.to_string_lossy().into_owned()),
                ),
            );
            return;
        }
        match TcpListener::bind(("127.0.0.1", 8080)) {
            Ok(listener) => drop(listener),
            Err(_) => {
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        "Port 8080 is already in use. Close the other service and retry.",
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                return;
            }
        }

        let (jar, java) = match backend_launch_paths(&app) {
            Ok(paths) => paths,
            Err(error) => {
                self.log_startup_diagnostic(&error);
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        error,
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                return;
            }
        };
        if !jar.is_file() {
            self.log_startup_diagnostic(format!("Backend JAR not found at {}.", jar.display()));
            self.set_status(
                &app,
                BackendStatus::new(
                    "failed",
                    format!("Backend JAR not found at {}.", jar.display()),
                    Some(self.log_path.to_string_lossy().into_owned()),
                ),
            );
            return;
        }
        if !cfg!(debug_assertions) && !java.is_file() {
            self.log_startup_diagnostic(format!(
                "Bundled Java runtime not found at {}.",
                java.display()
            ));
            let message = format!(
                "Bundled Java runtime not found at {}. Reinstall FocusDesktop and inspect the local diagnostics log.",
                java.display()
            );
            self.set_status(
                &app,
                BackendStatus::new(
                    "failed",
                    message,
                    Some(self.log_path.to_string_lossy().into_owned()),
                ),
            );
            return;
        }
        let log = match OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_path)
        {
            Ok(file) => file,
            Err(error) => {
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        format!("Could not open backend diagnostics log: {error}"),
                        None,
                    ),
                );
                return;
            }
        };
        let stderr = match log.try_clone() {
            Ok(file) => file,
            Err(error) => {
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        format!("Could not prepare backend diagnostics log: {error}"),
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                return;
            }
        };
        let mut command = Command::new(&java);
        command
            .arg(format!(
                "-Dfocusdesktop.backend-jar={}",
                java_compatible_path(&jar).display()
            ))
            .arg("-jar")
            .arg(java_compatible_path(&jar))
            .arg("--server.address=127.0.0.1")
            .arg("--server.port=8080")
            .arg("--focusdesktop.blocking.monitor-enabled=true")
            .env("FOCUSDESKTOP_DATA_DIR", &self.data_dir)
            .stdin(Stdio::null())
            .stdout(Stdio::from(log))
            .stderr(Stdio::from(stderr));
        #[cfg(windows)]
        command.creation_flags(0x08000000); // CREATE_NO_WINDOW
        let child = command.spawn();
        let child = match child {
            Ok(child) => child,
            Err(error) => {
                let recovery = if cfg!(debug_assertions) {
                    "Check that Java 17+ is installed and available, then retry."
                } else {
                    "The bundled Java runtime could not start. Reinstall FocusDesktop and inspect the local diagnostics log."
                };
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        format!("Could not start Java backend: {error}. {recovery}"),
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                return;
            }
        };
        *self.child.lock().expect("backend child mutex poisoned") = Some(child);

        self.set_status(
            &app,
            BackendStatus::new(
                "starting",
                "Waiting for the local API to become healthy…",
                Some(self.log_path.to_string_lossy().into_owned()),
            ),
        );
        let client = Client::builder()
            .timeout(Duration::from_millis(700))
            .build();
        let started = Instant::now();
        loop {
            if self.stop.load(Ordering::SeqCst) {
                self.stop_child();
                return;
            }
            let exited = self
                .child
                .lock()
                .expect("backend child mutex poisoned")
                .as_mut()
                .and_then(|child| child.try_wait().ok().flatten());
            if let Some(exit) = exited {
                *self.child.lock().expect("backend child mutex poisoned") = None;
                self.set_status(&app, BackendStatus::new("failed", format!("The backend exited during startup ({exit}). See the diagnostics log and retry."), Some(self.log_path.to_string_lossy().into_owned())));
                return;
            }
            let healthy = client
                .as_ref()
                .ok()
                .and_then(|client| client.get(format!("{API_BASE}/health")).send().ok())
                .is_some_and(|response| response.status().is_success());
            if healthy {
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "ready",
                        "Local API ready",
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                break;
            }
            if started.elapsed() >= HEALTH_TIMEOUT {
                self.log_startup_diagnostic(format!(
                    "Backend health check timed out after {} seconds; stopping the backend.",
                    HEALTH_TIMEOUT.as_secs()
                ));
                self.set_status(&app, BackendStatus::new("failed", format!("The backend did not become healthy within {} seconds. Review the diagnostics log and retry.", HEALTH_TIMEOUT.as_secs()), Some(self.log_path.to_string_lossy().into_owned())));
                self.stop_child();
                return;
            }
            thread::sleep(Duration::from_millis(250));
        }

        while !self.stop.load(Ordering::SeqCst) {
            thread::sleep(Duration::from_millis(500));
            let exited = self
                .child
                .lock()
                .expect("backend child mutex poisoned")
                .as_mut()
                .and_then(|child| child.try_wait().ok().flatten());
            if let Some(exit) = exited {
                *self.child.lock().expect("backend child mutex poisoned") = None;
                self.set_status(
                    &app,
                    BackendStatus::new(
                        "failed",
                        format!(
                            "The local backend stopped unexpectedly ({exit}). Retry to restart it."
                        ),
                        Some(self.log_path.to_string_lossy().into_owned()),
                    ),
                );
                return;
            }
        }
        self.stop_child();
    }

    fn stop_child(&self) {
        if let Some(mut child) = self
            .child
            .lock()
            .expect("backend child mutex poisoned")
            .take()
        {
            terminate_child(&mut child, &self.data_dir);
        }
    }

    fn shutdown(&self, app: &AppHandle) {
        self.stop.store(true, Ordering::SeqCst);
        self.stop_child();
        if let Some(worker) = self
            .worker
            .lock()
            .expect("backend worker mutex poisoned")
            .take()
        {
            let _ = worker.join();
        }
        self.set_status(
            app,
            BackendStatus::new(
                "stopped",
                "Local API stopped",
                Some(self.log_path.to_string_lossy().into_owned()),
            ),
        );
    }

    fn log_startup_diagnostic(&self, message: impl AsRef<str>) {
        if let Ok(mut log) = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_path)
        {
            let _ = writeln!(log, "{}", message.as_ref());
        }
    }
}

#[cfg(unix)]
fn terminate_child(child: &mut Child, data_dir: &PathBuf) {
    request_backend_shutdown(child, data_dir);
    if child.try_wait().ok().flatten().is_some() {
        return;
    }
    unsafe {
        libc::kill(child.id() as i32, libc::SIGTERM);
    }
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        if child.try_wait().ok().flatten().is_some() {
            return;
        }
        thread::sleep(Duration::from_millis(100));
    }
    let _ = child.kill();
    let _ = child.wait();
}

#[cfg(windows)]
fn terminate_child(child: &mut Child, data_dir: &PathBuf) {
    request_backend_shutdown(child, data_dir);
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        if child.try_wait().ok().flatten().is_some() {
            return;
        }
        thread::sleep(Duration::from_millis(100));
    }
    let _ = child.kill();
    let _ = child.wait();
}

fn request_backend_shutdown(child: &mut Child, data_dir: &PathBuf) {
    if child.try_wait().ok().flatten().is_some() {
        return;
    }
    let token = match fs::read_to_string(data_dir.join("api-token")) {
        Ok(token) => token.trim().to_owned(),
        Err(_) => return,
    };
    let client = match Client::builder().timeout(Duration::from_secs(2)).build() {
        Ok(client) => client,
        Err(_) => return,
    };
    let _ = client
        .post(format!("{API_BASE}/internal/shutdown"))
        .bearer_auth(token)
        .header("Origin", "tauri://localhost")
        .send();
}

fn backend_launch_paths(app: &AppHandle) -> Result<(PathBuf, PathBuf), String> {
    if cfg!(debug_assertions) {
        let jar = env::var_os("FOCUSDESKTOP_BACKEND_JAR")
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                    .join("../../backend/target/focus-and-study-tracking-0.0.1-SNAPSHOT.jar")
            });
        let jar = jar.canonicalize().unwrap_or(jar);
        let java = env::var_os("FOCUSDESKTOP_JAVA")
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                PathBuf::from(if cfg!(target_os = "windows") {
                    "java.exe"
                } else {
                    "java"
                })
            });
        return Ok((jar, java));
    }

    let resources = app
        .path()
        .resource_dir()
        .ok()
        .or_else(|| {
            #[cfg(target_os = "macos")]
            {
                env::current_exe()
                    .ok()
                    .and_then(|executable| {
                        executable
                            .parent()?
                            .parent()
                            .map(|contents| contents.join("Resources"))
                    })
                    .filter(|resources| resources.is_dir())
            }
            #[cfg(not(target_os = "macos"))]
            {
                None
            }
        })
        .ok_or_else(|| "Could not locate the packaged application resources".to_owned())?;
    let java_name = if cfg!(target_os = "windows") {
        "java.exe"
    } else {
        "java"
    };
    Ok((
        resources.join("backend/focusdesktop.jar"),
        resources.join("runtime/bin").join(java_name),
    ))
}

fn java_compatible_path(path: &Path) -> PathBuf {
    #[cfg(target_os = "windows")]
    {
        if let Some(path) = path.to_string_lossy().strip_prefix("\\\\?\\") {
            if let Some(unc_path) = path.strip_prefix("UNC\\") {
                return PathBuf::from(format!("\\\\{unc_path}"));
            }
            return PathBuf::from(path);
        }
    }
    path.to_path_buf()
}

fn app_data_dir() -> io::Result<PathBuf> {
    if let Some(path) = env::var_os("FOCUSDESKTOP_DATA_DIR") {
        let path = PathBuf::from(path);
        return Ok(if path.is_absolute() {
            path
        } else {
            home_dir()?.join(path)
        });
    }
    #[cfg(target_os = "windows")]
    {
        return Ok(env::var_os("LOCALAPPDATA")
            .map(PathBuf::from)
            .unwrap_or(home_dir()?.join("AppData/Local"))
            .join("FocusDesktop"));
    }
    #[cfg(target_os = "macos")]
    {
        return Ok(home_dir()?.join("Library/Application Support/FocusDesktop"));
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        if let Some(path) = env::var_os("XDG_DATA_HOME") {
            let path = PathBuf::from(path);
            if path.is_absolute() {
                return Ok(path.join("focusdesktop"));
            }
        }
        Ok(home_dir()?.join(".local/share/focusdesktop"))
    }
}

fn home_dir() -> io::Result<PathBuf> {
    env::var_os("HOME")
        .or_else(|| env::var_os("USERPROFILE"))
        .map(PathBuf::from)
        .ok_or_else(|| {
            io::Error::new(
                io::ErrorKind::NotFound,
                "Could not resolve the current user's home directory",
            )
        })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct NativeHttpResponse {
    status: u16,
    body: Option<serde_json::Value>,
}

fn api_token(supervisor: &BackendSupervisor) -> Result<String, String> {
    fs::read_to_string(supervisor.data_dir.join("api-token"))
        .map(|token| token.trim().to_owned())
        .map_err(|_| {
            "The API token is not available yet. Retry after the backend is ready.".to_owned()
        })
}

#[tauri::command]
fn backend_status(supervisor: State<'_, Arc<BackendSupervisor>>) -> BackendStatus {
    supervisor.status()
}

#[tauri::command]
async fn save_history_csv(app: AppHandle, csv: String) -> Result<bool, String> {
    let result = tauri::async_runtime::spawn_blocking(move || {
        let Some(file_path) = app
            .dialog()
            .file()
            .add_filter("CSV", &["csv"])
            .set_file_name("focus-history.csv")
            .blocking_save_file()
        else {
            return Ok(false);
        };
        let path = file_path
            .into_path()
            .map_err(|error| format!("Could not use the selected export path: {error}"))?;
        fs::write(path, csv.as_bytes())
            .map_err(|error| format!("Could not save CSV file: {error}"))?;
        Ok(true)
    })
    .await
    .map_err(|error| format!("CSV save operation failed: {error}"))?;
    result
}

#[tauri::command]
async fn save_data_export(
    app: AppHandle,
    format: String,
    supervisor: State<'_, Arc<BackendSupervisor>>,
) -> Result<bool, String> {
    if supervisor.status().phase != "ready" {
        return Err("The local API is not ready yet.".to_owned());
    }
    let (extension, mime, file_name) = match format.as_str() {
        "json" => ("json", "JSON", "focusdesktop-export.json"),
        "csv-zip" => ("zip", "ZIP archive", "focusdesktop-export.zip"),
        _ => return Err("Unsupported export format.".to_owned()),
    };
    let token = api_token(&supervisor)?;
    let result = tauri::async_runtime::spawn_blocking(move || {
        let response = Client::new()
            .get(format!("{API_BASE}/api/v1/settings/export?format={format}"))
            .bearer_auth(token)
            .header("Origin", "tauri://localhost")
            .send()
            .map_err(|error| format!("Could not export local data: {error}"))?;
        if !response.status().is_success() {
            return Err(format!(
                "The local API export failed with HTTP {}.",
                response.status()
            ));
        }
        let bytes = response.bytes().map_err(|error| error.to_string())?;
        let Some(file_path) = app
            .dialog()
            .file()
            .add_filter(mime, &[extension])
            .set_file_name(file_name)
            .blocking_save_file()
        else {
            return Ok(false);
        };
        let path = file_path
            .into_path()
            .map_err(|error| format!("Could not use the selected export path: {error}"))?;
        fs::write(path, bytes.as_ref())
            .map_err(|error| format!("Could not save data export: {error}"))?;
        Ok(true)
    })
    .await
    .map_err(|error| format!("Data export failed: {error}"))?;
    result
}

#[tauri::command]
fn open_data_folder(supervisor: State<'_, Arc<BackendSupervisor>>) -> Result<(), String> {
    fs::create_dir_all(&supervisor.data_dir)
        .map_err(|error| format!("Could not access the data directory: {error}"))?;
    let mut command = if cfg!(target_os = "windows") {
        Command::new("explorer")
    } else if cfg!(target_os = "macos") {
        Command::new("open")
    } else {
        Command::new("xdg-open")
    };
    command
        .arg(&supervisor.data_dir)
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("Could not open the data directory: {error}"))
}

#[tauri::command]
fn retry_backend(
    app: AppHandle,
    supervisor: State<'_, Arc<BackendSupervisor>>,
) -> Result<(), String> {
    if supervisor.status().phase == "starting" {
        return Ok(());
    }
    if supervisor
        .child
        .lock()
        .map_err(|_| "Backend process lock is unavailable".to_owned())?
        .is_some()
    {
        return Err(
            "The backend process is already running. Wait for its health check to finish."
                .to_owned(),
        );
    }
    supervisor.start(app);
    Ok(())
}

#[tauri::command]
fn api_request(
    method: String,
    path: String,
    body: Option<serde_json::Value>,
    supervisor: State<'_, Arc<BackendSupervisor>>,
) -> Result<NativeHttpResponse, String> {
    if supervisor.status().phase != "ready" {
        return Err("The local API is not ready yet.".to_owned());
    }
    if !(path.starts_with("/api/v1/") || path == "/api/v1/sessions") || path.contains("..") {
        return Err("Unsupported API path".to_owned());
    }
    let method = match method.as_str() {
        "GET" => reqwest::Method::GET,
        "POST" => reqwest::Method::POST,
        "PUT" => reqwest::Method::PUT,
        "PATCH" => reqwest::Method::PATCH,
        "DELETE" => reqwest::Method::DELETE,
        _ => return Err("Unsupported API method".to_owned()),
    };
    let token = api_token(&supervisor)?;
    let client = Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|e| e.to_string())?;
    let mut request = client
        .request(method, format!("{API_BASE}{path}"))
        .bearer_auth(token)
        .header("Origin", "tauri://localhost")
        .header("Accept", "application/json");
    if let Some(body) = body {
        request = request.json(&body);
    }
    let response = request
        .send()
        .map_err(|error| format!("Could not reach the local API: {error}"))?;
    let status = response.status().as_u16();
    let body = if status == 204 {
        None
    } else {
        Some(
            response
                .json()
                .map_err(|error| format!("The local API returned invalid JSON: {error}"))?,
        )
    };
    Ok(NativeHttpResponse { status, body })
}

#[tauri::command]
fn connect_session_websocket(
    connection_id: String,
    app: AppHandle,
    supervisor: State<'_, Arc<BackendSupervisor>>,
) -> Result<(), String> {
    if supervisor.status().phase != "ready" {
        return Err("The local API is not ready yet.".to_owned());
    }
    let token = api_token(&supervisor)?;
    let stop = Arc::new(AtomicBool::new(false));
    websocket_controls()
        .lock()
        .map_err(|_| "WebSocket control lock is unavailable".to_owned())?
        .insert(connection_id.clone(), Arc::clone(&stop));
    thread::spawn(move || {
        let mut request = match "ws://127.0.0.1:8080/ws".into_client_request() {
            Ok(request) => request,
            Err(error) => {
                emit_ws(&app, &connection_id, "error", error.to_string());
                return;
            }
        };
        let headers = request.headers_mut();
        headers.insert("Authorization", format!("Bearer {token}").parse().unwrap());
        headers.insert("Origin", "tauri://localhost".parse().unwrap());
        let (mut socket, _) = match connect(request) {
            Ok(connection) => connection,
            Err(error) => {
                emit_ws(&app, &connection_id, "error", error.to_string());
                emit_ws(&app, &connection_id, "close", "connection failed");
                return;
            }
        };
        if let MaybeTlsStream::Plain(stream) = socket.get_mut() {
            let _ = stream.set_read_timeout(Some(Duration::from_millis(500)));
        }
        emit_ws(&app, &connection_id, "open", "");
        while !stop.load(Ordering::SeqCst) {
            match socket.read() {
                Ok(Message::Text(text)) => {
                    emit_ws(&app, &connection_id, "message", text.to_string())
                }
                Ok(Message::Close(_)) => break,
                Ok(_) => {}
                Err(tungstenite::Error::Io(error))
                    if error.kind() == io::ErrorKind::WouldBlock
                        || error.kind() == io::ErrorKind::TimedOut => {}
                Err(error) => {
                    emit_ws(&app, &connection_id, "error", error.to_string());
                    break;
                }
            }
        }
        let _ = socket.close(None);
        emit_ws(&app, &connection_id, "close", "");
        if let Ok(mut controls) = websocket_controls().lock() {
            controls.remove(&connection_id);
        }
    });
    Ok(())
}

#[tauri::command]
fn disconnect_session_websocket(connection_id: String) {
    if let Ok(mut controls) = websocket_controls().lock() {
        if let Some(stop) = controls.remove(&connection_id) {
            stop.store(true, Ordering::SeqCst);
        }
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct WsEvent {
    connection_id: String,
    kind: String,
    data: String,
}

fn emit_ws(app: &AppHandle, id: &str, kind: &str, data: impl Into<String>) {
    let _ = app.emit(
        "session-websocket",
        WsEvent {
            connection_id: id.to_owned(),
            kind: kind.to_owned(),
            data: data.into(),
        },
    );
}

fn websocket_controls() -> &'static Mutex<std::collections::HashMap<String, Arc<AtomicBool>>> {
    static CONTROLS: std::sync::OnceLock<
        Mutex<std::collections::HashMap<String, Arc<AtomicBool>>>,
    > = std::sync::OnceLock::new();
    CONTROLS.get_or_init(|| Mutex::new(std::collections::HashMap::new()))
}

pub fn run() {
    let supervisor = Arc::new(
        BackendSupervisor::new()
            .expect("could not resolve FocusDesktop application data directory"),
    );
    let supervisor_for_exit = Arc::clone(&supervisor);
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .manage(supervisor.clone())
        .setup(move |app| {
            supervisor.start(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            backend_status,
            retry_backend,
            api_request,
            save_history_csv,
            save_data_export,
            open_data_folder,
            connect_session_websocket,
            disconnect_session_websocket
        ])
        .build(tauri::generate_context!())
        .expect("error while building FocusDesktop")
        .run(move |app, event| {
            if matches!(
                event,
                tauri::RunEvent::ExitRequested { .. } | tauri::RunEvent::Exit
            ) {
                supervisor_for_exit.shutdown(&app);
            }
        });
}
