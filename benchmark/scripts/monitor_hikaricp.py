"""
monitor_hikaricp.py
论文 6.1.2 节：每 5 秒轮询 Activity 服务 Actuator 端点，
采集 HikariCP 活跃连接数，打印带时间戳的日志并追加写入 hikaricp_metrics.csv。

用法：
    python monitor_hikaricp.py

依赖：
    pip install requests
"""

import csv
import os
import time
from datetime import datetime
from typing import Optional

import requests

ACTUATOR_URL = (
    "http://localhost:9092/actuator/metrics/hikaricp.connections.active"
)
OUTPUT_CSV = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          "hikaricp_metrics.csv")
INTERVAL_SEC = 5


def fetch_active_connections() -> Optional[float]:
    try:
        resp = requests.get(ACTUATOR_URL, timeout=3)
        resp.raise_for_status()
        data = resp.json()
        return data["measurements"][0]["value"]
    except requests.exceptions.ConnectionError:
        print("  [WARN] Activity 服务未启动或端口不可达，跳过本次采样")
        return None
    except Exception as e:
        print(f"  [WARN] 采样失败：{e}")
        return None


def main():
    # 写 CSV 表头（文件不存在时）
    write_header = not os.path.exists(OUTPUT_CSV)
    with open(OUTPUT_CSV, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(["timestamp", "active_connections"])

    print(f"开始监控 HikariCP 活跃连接数，每 {INTERVAL_SEC} 秒采样一次")
    print(f"数据写入：{OUTPUT_CSV}")
    print("按 Ctrl+C 停止\n")

    try:
        while True:
            ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            value = fetch_active_connections()

            if value is not None:
                print(f"[{ts}]  HikariCP 活跃连接数：{int(value)}")
                with open(OUTPUT_CSV, "a", newline="", encoding="utf-8") as f:
                    csv.writer(f).writerow([ts, int(value)])
            else:
                print(f"[{ts}]  HikariCP 活跃连接数：N/A")

            time.sleep(INTERVAL_SEC)

    except KeyboardInterrupt:
        print("\n监控已停止。")


if __name__ == "__main__":
    main()
