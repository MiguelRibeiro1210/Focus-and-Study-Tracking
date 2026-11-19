import { createHash } from 'node:crypto';
import { chmod, cp, mkdtemp, mkdir, readFile, readdir, rm, stat, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(scriptDir, '..');
const repoDir = path.resolve(frontendDir, '..');
const resourceDir = path.join(frontendDir, 'src-tauri', 'resources');
const manifest = JSON.parse(await readFile(path.join(scriptDir, 'runtime-manifest.json'), 'utf8'));

const platform = process.platform;
const arch = process.arch;
const supportedBuild = ['linux/x64', 'darwin/x64', 'darwin/arm64', 'win32/x64'];
if (!supportedBuild.includes(`${platform}/${arch}`)) {
  throw new Error(`Unsupported bundle build host: ${platform}/${arch}. Supported hosts: ${supportedBuild.join(', ')}.`);
}

const osKey = platform === 'darwin' ? 'macos' : platform === 'win32' ? 'windows' : 'linux';
const runtimeArch = arch === 'arm64' ? 'arm64' : 'x64';
const asset = manifest.assets[`${osKey}-${runtimeArch}`];
if (!asset) throw new Error(`No pinned Temurin JRE configured for ${osKey}-${runtimeArch}.`);
const extension = asset.archive.endsWith('.zip') ? 'zip' : 'tar.gz';
const releaseUrl = `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${manifest.version}/${asset.archive}`;
const tempDir = await mkdtemp(path.join(tmpdir(), 'focusdesktop-bundle-'));
const archivePath = path.join(tempDir, asset.archive);
const extractedDir = path.join(tempDir, 'extracted');
const runtimeDir = path.join(resourceDir, 'runtime');
const javaExecutable = platform === 'win32' ? 'java.exe' : 'java';

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: 'inherit', ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with status ${result.status}`);
}

async function findJavaHome(dir, depth = 0) {
  if (depth > 6) return null;
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const child = path.join(dir, entry.name);
    const executable = path.join(child, 'bin', javaExecutable);
    try {
      if ((await stat(executable)).isFile()) return child;
    } catch {
      const nested = await findJavaHome(child, depth + 1);
      if (nested) return nested;
    }
  }
  return null;
}

async function makeResourcesWritable(dir) {
  const metadata = await stat(dir);
  await chmod(dir, (metadata.mode & 0o777) | 0o700);
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const entryPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      await makeResourcesWritable(entryPath);
    } else {
      const entryMetadata = await stat(entryPath);
      await chmod(entryPath, (entryMetadata.mode & 0o777) | 0o200);
    }
  }
}

try {
  const backendWrapper = platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  const wrapperCommand = platform === 'win32' ? 'cmd.exe' : backendWrapper;
  const wrapperArgs = platform === 'win32'
    ? ['/d', '/s', '/c', 'mvnw.cmd -DskipTests package']
    : ['-DskipTests', 'package'];
  console.log('Building the Spring Boot backend JAR…');
  run(wrapperCommand, wrapperArgs, { cwd: path.join(repoDir, 'backend') });

  const backendJar = path.join(repoDir, 'backend', 'target', 'focus-and-study-tracking-0.0.1-SNAPSHOT.jar');
  if (!(await stat(backendJar)).isFile()) throw new Error(`Backend JAR was not created: ${backendJar}`);

  console.log(`Downloading Eclipse Temurin JRE ${manifest.version} for ${osKey}-${runtimeArch}…`);
  run('curl', [
    '-fL', '--retry', '3', '--connect-timeout', '20', '--max-time', '600',
    '--silent', '--show-error', '--output', archivePath, releaseUrl,
  ]);
  const actualHash = createHash('sha256').update(await readFile(archivePath)).digest('hex');
  if (actualHash !== asset.sha256) {
    throw new Error(`Temurin archive checksum mismatch. Expected ${asset.sha256}, received ${actualHash}`);
  }
  await mkdir(extractedDir, { recursive: true });
  if (extension === 'zip') {
    const quotedArchive = archivePath.replaceAll("'", "''");
    const quotedDestination = extractedDir.replaceAll("'", "''");
    run('powershell.exe', [
      '-NoProfile', '-NonInteractive', '-Command',
      `Expand-Archive -LiteralPath '${quotedArchive}' -DestinationPath '${quotedDestination}' -Force`,
    ]);
  } else {
    run('tar', ['-xzf', archivePath, '-C', extractedDir, '--no-same-owner']);
  }

  const javaHome = await findJavaHome(extractedDir);
  if (!javaHome) throw new Error(`Temurin archive did not contain bin/${javaExecutable}`);
  console.log('Verifying the staged Java runtime…');
  run(path.join(javaHome, 'bin', javaExecutable), ['-version']);

  await mkdir(path.join(resourceDir, 'backend'), { recursive: true });
  await mkdir(runtimeDir, { recursive: true });
  for (const entry of await readdir(runtimeDir)) {
    if (entry !== '.gitkeep') await rm(path.join(runtimeDir, entry), { recursive: true, force: true });
  }
  await rm(path.join(resourceDir, 'backend', 'focusdesktop.jar'), { force: true });
  await cp(backendJar, path.join(resourceDir, 'backend', 'focusdesktop.jar'));
  await cp(javaHome, runtimeDir, { recursive: true, dereference: true });
  if (platform === 'linux') {
    // linuxdeploy scans every ELF dependency in the AppDir. AWT libraries in
    // the JRE look for libjvm.so beside themselves, while Temurin stores it in
    // lib/server. Keep the relative link so the scan and runtime loader resolve it.
    const jvmLibrary = path.join(runtimeDir, 'lib', 'server', 'libjvm.so');
    if (!(await stat(jvmLibrary)).isFile()) {
      throw new Error(`Temurin runtime is missing the JVM library: ${jvmLibrary}`);
    }
    const jvmLink = path.join(runtimeDir, 'lib', 'libjvm.so');
    await rm(jvmLink, { force: true });
    await symlink('server/libjvm.so', jvmLink);
  }
  if (platform !== 'win32') await chmod(path.join(runtimeDir, 'bin', javaExecutable), 0o755);
  await makeResourcesWritable(resourceDir);

  // tauri-build copies resources into these generated profile folders. Remove
  // old copies so read-only files from an earlier build cannot block refresh.
  for (const profile of ['debug', 'release']) {
    for (const resource of ['backend', 'runtime']) {
      await rm(path.join(frontendDir, 'src-tauri', 'target', profile, resource), {
        recursive: true,
        force: true,
      });
    }
  }

  console.log(`Bundle resources staged under ${resourceDir}`);
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
