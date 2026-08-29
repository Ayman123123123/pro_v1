$root = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
$inputPath = Join-Path $root 'build-logs\android-ui-window.xml'
$outputPath = Join-Path $root 'build-logs\android-ui-window-formatted.xml'
$doc = New-Object System.Xml.XmlDocument
$doc.PreserveWhitespace = $false
$doc.Load($inputPath)
$settings = New-Object System.Xml.XmlWriterSettings
$settings.Indent = $true
$settings.Encoding = New-Object System.Text.UTF8Encoding($false)
$writer = [System.Xml.XmlWriter]::Create($outputPath, $settings)
$doc.Save($writer)
$writer.Dispose()
Get-Item $outputPath | Select-Object FullName, Length, LastWriteTime
