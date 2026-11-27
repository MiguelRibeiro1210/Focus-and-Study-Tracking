ObjC.import('Foundation');
var fm = $.NSFileManager.defaultManager;
var apps = [], warnings = [], visited = 0;
function text(value) { return value && !value.isNil() ? String(ObjC.unwrap(value)) : ''; }
function walk(path, depth) {
    if (depth > 3 || visited > 10000 || apps.length >= 4096) return;
    var error = Ref();
    var names = fm.contentsOfDirectoryAtPathError($(path), error);
    if (!names || names.isNil()) {
        if (fm.fileExistsAtPath($(path))) warnings.push('Some application folders could not be read.');
        return;
    }
    var children = ObjC.deepUnwrap(names);
    for (var index = 0; index < children.length && visited++ < 10000 && apps.length < 4096; index++) {
        var child = path + '/' + children[index];
        if (/\.app$/i.test(child)) {
            var bundle = $.NSBundle.bundleWithPath($(child));
            if (!bundle || bundle.isNil()) continue;
            var info = bundle.infoDictionary;
            var name = text(info.objectForKey($('CFBundleDisplayName'))) || text(info.objectForKey($('CFBundleName'))) || children[index].slice(0, -4);
            var identity = text(bundle.bundleIdentifier);
            var executable = text(bundle.executablePath);
            apps.push({displayName:name,source:'macOS app',applicationId:identity ? 'macos:' + identity : '',executable:executable,key:child});
        } else if (depth < 3) {
            var directory = Ref();
            if (fm.fileExistsAtPathIsDirectory($(child), directory) && directory[0]) walk(child, depth + 1);
        }
    }
}
var home = text($.NSHomeDirectory());
['/Applications', '/System/Applications', '/System/Library/CoreServices/Applications', home + '/Applications'].forEach(function(path) { walk(path, 0); });
if (visited >= 10000 || apps.length >= 4096) warnings.push('Some applications were omitted because the discovery limit was reached.');
JSON.stringify({applications:apps,warnings:warnings});
