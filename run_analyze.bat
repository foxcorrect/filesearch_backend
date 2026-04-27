cd e:/code/filesearch/resume-backend
set CP=target\test-classes;target\classes
for /R "%USERPROFILE%\.m2\repository" %%j in (*.jar) do call set CP=%%CP%%;%%j
java -cp "%CP%" com.resume.service.PdfAnalyzeTest
