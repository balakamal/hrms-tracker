import json
import os
import re
import subprocess
from datetime import datetime

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    build_gradle_path = os.path.join(root_dir, "android", "app", "build.gradle")
    
    # 1. Extract versionName from build.gradle
    version_name = "4.3"
    if os.path.exists(build_gradle_path):
        with open(build_gradle_path, "r", encoding="utf-8") as f:
            content = f.read()
            match = re.search(r'versionName\s+["\']([^"\']+)["\']', content)
            if match:
                version_name = match.group(1)

    # 2. Extract or compute versionCode
    version_code = 21
    github_run_number = os.environ.get("GITHUB_RUN_NUMBER")
    if github_run_number:
        try:
            version_code = 100 + int(github_run_number)
        except ValueError:
            pass

    apk_path = os.path.join(root_dir, "builds", "hrms-tracker-latest.apk")
    android_home = os.environ.get("ANDROID_HOME", "")
    if android_home and os.path.exists(apk_path):
        apkanalyzer = os.path.join(android_home, "cmdline-tools", "latest", "bin", "apkanalyzer")
        if os.path.exists(apkanalyzer):
            try:
                res = subprocess.run([apkanalyzer, "manifest", "version-code", apk_path],
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                code_str = res.stdout.strip()
                if code_str.isdigit():
                    version_code = int(code_str)
            except Exception:
                pass

    # 3. Calculate APK file size
    apk_size = "6.2 MB"
    if os.path.exists(apk_path):
        size_bytes = os.path.getsize(apk_path)
        apk_size = f"{size_bytes / (1024 * 1024):.1f} MB"

    # 4. Commit title as release notes
    commit_title = "Performance enhancements, widget refinements, and update notifications."
    try:
        res = subprocess.run(["git", "log", "-1", "--pretty=%s"],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=root_dir)
        title = res.stdout.strip()
        if title:
            commit_title = title
    except Exception:
        pass

    today = datetime.now().strftime("%Y-%m-%d")

    data = {
        "versionCode": version_code,
        "versionName": version_name,
        "releaseDate": today,
        "releaseNotes": commit_title,
        "apkUrl": "https://raw.githubusercontent.com/balakamal/hrms-tracker/main/builds/hrms-tracker-latest.apk",
        "apkSize": apk_size
    }

    output_path = os.path.join(root_dir, "version.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"Generated {output_path} successfully:\n{json.dumps(data, indent=2)}")

if __name__ == "__main__":
    main()
