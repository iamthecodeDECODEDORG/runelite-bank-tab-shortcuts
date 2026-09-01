[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$pluginRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedWrapperHash = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $localTools = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.local\tools'
    $jdk = Get-ChildItem -Path (Join-Path $localTools 'temurin-*\jdk-*') -Directory -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $jdk) {
        throw 'BTS_VERIFY_JDK_MISSING: Install a JDK 17+ or set JAVA_HOME.'
    }
    $env:JAVA_HOME = $jdk.FullName
}

$wrapperJar = Join-Path $pluginRoot 'gradle\wrapper\gradle-wrapper.jar'
$wrapperHash = (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($wrapperHash -ne $expectedWrapperHash) {
    throw "BTS_VERIFY_WRAPPER_JAR_HASH: expected $expectedWrapperHash, found $wrapperHash"
}

$wrapperProperties = Get-Content -Raw -LiteralPath (Join-Path $pluginRoot 'gradle\wrapper\gradle-wrapper.properties')
if ($wrapperProperties -notmatch 'distributionSha256Sum=5b9c5eb3f9fc2c94abaea57d90bd78747ca117ddbbf96c859d3741181a12bf2a' -or
        $wrapperProperties -notmatch 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.10-bin.zip') {
    throw 'BTS_VERIFY_WRAPPER_PROPERTIES: Gradle distribution URL or checksum is not pinned'
}

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$gradle = @('-classpath', $wrapperJar, 'org.gradle.wrapper.GradleWrapperMain')
$arguments = @('-p', $pluginRoot, '--no-daemon', '--dependency-verification', 'strict')

& $java @gradle @arguments clean test jar
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_GRADLE_FAILED: exit $LASTEXITCODE"
}

$jars = @(Get-ChildItem -LiteralPath (Join-Path $pluginRoot 'build\libs') -Filter '*.jar' -File)
if ($jars.Count -ne 1) {
    throw "BTS_VERIFY_JAR_COUNT: expected one plugin JAR, found $($jars.Count)"
}
$jar = $jars[0].FullName

python (Join-Path $PSScriptRoot 'security_scan.py') --source (Join-Path $pluginRoot 'src\main\java') --jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_SECURITY_SCAN_FAILED: exit $LASTEXITCODE"
}
python (Join-Path $PSScriptRoot 'test_security_scan.py')
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_SECURITY_MUTATION_FAILED: exit $LASTEXITCODE"
}

$runtimeDependencies = (& $java @gradle @arguments -q dependencies --configuration runtimeClasspath 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_RUNTIME_DEPS_FAILED: $runtimeDependencies"
}
if ($runtimeDependencies -notmatch 'No dependencies') {
    throw "BTS_VERIFY_RUNTIME_DEPS_PRESENT: $runtimeDependencies"
}

git -C $pluginRoot diff --check
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_DIFF_CHECK_FAILED: exit $LASTEXITCODE"
}

$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
& $java @gradle @arguments clean jar
if ($LASTEXITCODE -ne 0) {
    throw "BTS_VERIFY_REPRODUCIBLE_REBUILD_FAILED: exit $LASTEXITCODE"
}
$rebuiltJar = @(Get-ChildItem -LiteralPath (Join-Path $pluginRoot 'build\libs') -Filter '*.jar' -File)
if ($rebuiltJar.Count -ne 1) {
    throw "BTS_VERIFY_REBUILT_JAR_COUNT: expected one plugin JAR, found $($rebuiltJar.Count)"
}
$rebuiltHash = (Get-FileHash -LiteralPath $rebuiltJar[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($rebuiltHash -ne $jarHash) {
    throw "BTS_VERIFY_NONDETERMINISTIC_JAR: first $jarHash, rebuilt $rebuiltHash"
}

Write-Output "BTS_VERIFY_OK jar=$($rebuiltJar[0].FullName) sha256=$rebuiltHash"
