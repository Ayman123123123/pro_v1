function Get-ContainerEnvValue {
    param([string]$Container, [string]$Key)
    $line = docker inspect $Container -f '{{range .Config.Env}}{{println .}}{{end}}' |
        Where-Object { $_ -like "$Key=*" } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return $line.Substring($Key.Length + 1)
}

$backendUser = Get-ContainerEnvValue -Container 'red-backend' -Key 'SPRING_DATASOURCE_USERNAME'
$backendPassword = Get-ContainerEnvValue -Container 'red-backend' -Key 'SPRING_DATASOURCE_PASSWORD'
$dbUser = Get-ContainerEnvValue -Container 'red-db-sql' -Key 'POSTGRES_USER'
$dbPassword = Get-ContainerEnvValue -Container 'red-db-sql' -Key 'POSTGRES_PASSWORD'

[pscustomobject]@{
    backend_username_present = -not [string]::IsNullOrEmpty($backendUser)
    backend_password_present = -not [string]::IsNullOrEmpty($backendPassword)
    database_username_present = -not [string]::IsNullOrEmpty($dbUser)
    database_password_present = -not [string]::IsNullOrEmpty($dbPassword)
    username_matches = ($backendUser -eq $dbUser)
    password_matches = ($backendPassword -eq $dbPassword)
} | ConvertTo-Json
