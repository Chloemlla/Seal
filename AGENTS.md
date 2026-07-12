Repository Guidelines

Do not write to a super file!!!! Do not write to a super file!!!! Do not write to a super file!!!!
All actual build and test commands must be executed within the GitHub workflow; running them on your local machine is prohibited—local device performance is insufficient.

don't run any Flutter and Gradle; modify the code.

Regarding the garbled text issue you mentioned, it has been confirmed that it is not caused by file corruption. The file can be read correctly in PowerShell using the following method:
powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
Get-Content -Encoding UTF8 file-path
Each time you complete the addition or modification of a feature according to my requirements, a commit message should be automatically generated and submitted and pushed after you finish modifying the code. When submitting a GPG key, you can temporarily omit the signature.
