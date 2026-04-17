"""
一键关闭并重启 smart-volunteer-activity 服务。
用法：
    python restart_activity.py
"""

import subprocess
import time
import os
import sys
import urllib.request

JAVA     = r"C:\Users\Annnn\.jdks\corretto-17.0.13\bin\java.exe"
JAVA8    = r"D:\jdk\jdk-1.8\bin\java.exe"
JAR      = r"D:\Project\smart-volunteer\smart-volunteer-backend\smart-volunteer-activity\target\smart-volunteer-activity-1.0-SNAPSHOT.jar"
LOG      = r"D:\Project\smart-volunteer\benchmark\scripts\activity.log"
BACKEND  = r"D:\Project\smart-volunteer\smart-volunteer-backend"
MAVEN    = r"D:\idea\apache-maven-3.9.9\bin\mvn.cmd"

REDIS_EXE  = r"D:\Redis\redis-server.exe"


def ensure_redis():
    """确保 Redis 在线，不在线则启动"""
    result = subprocess.run(["redis-cli", "ping"], capture_output=True, text=True, timeout=3)
    if result.stdout.strip() == "PONG":
        print("[INFO] Redis 已在线")
        return
    print("[INFO] Redis 未响应，正在启动...")
    subprocess.Popen(
        [REDIS_EXE],
        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
    )
    for _ in range(10):
        time.sleep(1)
        r = subprocess.run(["redis-cli", "ping"], capture_output=True, text=True, timeout=2)
        if r.stdout.strip() == "PONG":
            print("[INFO] Redis 就绪")
            return
    print("[WARN] Redis 启动超时")
NACOS_JAR  = r"C:\Users\Annnn\Desktop\杂\nacos-server-2.4.3\nacos\target\nacos-server.jar"
NACOS_LOG  = r"D:\Project\smart-volunteer\benchmark\scripts\nacos.log"


def find_pid_by_keyword(keyword):
    result = subprocess.run(
        ["wmic", "process", "where",
         f"CommandLine like '%{keyword}%' and name='java.exe'",
         "get", "ProcessId"],
        capture_output=True, text=True
    )
    return [l.strip() for l in result.stdout.splitlines() if l.strip().isdigit()]


def kill_by_keyword(keyword, label):
    pids = find_pid_by_keyword(keyword)
    if not pids:
        print(f"[INFO] {label} 未运行，跳过")
        return
    for pid in pids:
        subprocess.run(["taskkill", "/F", "/PID", pid], capture_output=True)
        print(f"[INFO] 已关闭 {label} PID={pid}")
    time.sleep(2)


def ensure_nacos():
    """确保 Nacos 在线，不在线则重启"""
    try:
        with urllib.request.urlopen(
                "http://localhost:8848/nacos/v1/console/health/liveness", timeout=2) as r:
            if r.status == 200:
                print("[INFO] Nacos 已在线")
                return
    except Exception:
        pass

    print("[INFO] Nacos 未响应，正在重启...")
    kill_by_keyword("nacos-server", "Nacos")
    log_f = open(NACOS_LOG, "w", encoding="utf-8")
    subprocess.Popen(
        [JAVA8, "-Dnacos.standalone=true", f"-Dnacos.home={NACOS_HOME}",
         "-jar", NACOS_JAR,
         f"--spring.config.additional-location=file:{NACOS_HOME}/conf/",
         "-m", "standalone"],
        stdout=log_f, stderr=log_f, cwd=os.path.join(NACOS_HOME, "bin")
    )
    for i in range(30):
        time.sleep(3)
        try:
            with urllib.request.urlopen(
                    "http://localhost:8848/nacos/v1/console/health/liveness", timeout=2) as r:
                if r.status == 200:
                    print(f"[INFO] Nacos 就绪 (t={i*3+3}s)")
                    return
        except Exception:
            print(f"  等待 Nacos... {(i+1)*3}s")
    print("[WARN] Nacos 启动超时，请手动检查")


def build():
    print("[INFO] 开始编译 smart-volunteer-activity ...")
    env = os.environ.copy()
    env["JAVA_HOME"] = r"C:\Users\Annnn\.jdks\corretto-17.0.13"
    result = subprocess.run(
        [MAVEN, "package", "-pl", "smart-volunteer-activity", "-am", "-DskipTests"],
        cwd=BACKEND, env=env
    )
    if result.returncode != 0:
        print("[ERROR] 编译失败，终止重启")
        sys.exit(1)
    print("[INFO] 编译成功")


def start():
    print(f"[INFO] 启动 activity 服务，日志 -> {LOG}")
    log_f = open(LOG, "w", encoding="utf-8")
    subprocess.Popen(
        [JAVA, "-jar", JAR],
        stdout=log_f, stderr=log_f,
        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
    )
    print("[INFO] 等待服务就绪", end="", flush=True)
    for _ in range(60):
        time.sleep(2)
        try:
            with urllib.request.urlopen(
                    "http://localhost:9092/actuator/health", timeout=2) as r:
                if r.status == 200:
                    print("\n[OK] activity 服务已就绪")
                    return
        except Exception:
            pass
        print(".", end="", flush=True)
    print("\n[WARN] 60s 内未检测到服务就绪，请手动确认")


if __name__ == "__main__":
    ensure_nacos()
    ensure_redis()
    kill_by_keyword("smart-volunteer-activity", "activity 服务")
    build()
    start()
