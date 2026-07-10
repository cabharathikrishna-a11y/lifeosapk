import sys

with open("gradle/libs.versions.toml", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if "firebase-crashlytics =" in line and "group =" in line:
        new_lines.append('firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }\n')
        new_lines.append('firebase-inappmessaging-display = { group = "com.google.firebase", name = "firebase-inappmessaging-display" }\n')

with open("gradle/libs.versions.toml", "w") as f:
    f.writelines(new_lines)
