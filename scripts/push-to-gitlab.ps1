# Push this project to a GitLab repository.
# Usage: .\scripts\push-to-gitlab.ps1 -GitlabUrl "https://gitlab.com/USERNAME/REPO.git" [-Branch "arena/01a0a2f4-pro-v1"]
param(
    [Parameter(Mandatory = $true)][string]$GitlabUrl,
    [string]$Branch = (git branch --show-current)
)
$ErrorActionPreference = "Stop"
$existing = git remote get-url gitlab 2>$null
if ($existing) {
    git remote set-url gitlab $GitlabUrl
    Write-Host "updated existing 'gitlab' remote -> $GitlabUrl"
} else {
    git remote add gitlab $GitlabUrl
    Write-Host "added 'gitlab' remote -> $GitlabUrl"
}
Write-Host "pushing '$Branch' to gitlab:main ..."
git push gitlab "$Branch`:main"
Write-Host "done. open your GitLab project > Build > Pipelines to watch CI."
