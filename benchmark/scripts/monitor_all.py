"""
monitor_all.py — 秒杀压测高频监控脚本
采样间隔 0.1s，10s 后自动退出，专为瞬时洪峰设计。

用法：
    python monitor_all.py exp
    python monitor_all.py ctrl
"""

import csv
import os
import subprocess
import sys
import time
from datetime import datetime
from typing import Optional

import requests

# ── 配置 ────────────────────────────────────────────────────
ACTUATOR_BASE = "http://localhost:9092/actuator/metrics"
REDIS_CLI     = "redis-cli"
INTERVAL_SEC  = 0.1   # 高频采样
MAX_DURATION  = 10    # 10秒后自动退出

BASE_DIR   = os.path.dirname(os.path.abspath(__file__))
OUTPUT_CSV = os.path.join(BASE_DIR, "monitor_metrics.csv")

FIELDNAMES = [
    "timestamp",
    "group",
    "system_cpu_pct",
    "process_cpu_pct",
    "jvm_heap_used_mb",
    "hikaricp_active",
    "hikaricp_pending",
    "redis_hit_rate_pct",
]

# ── Actuator 采集 ────────────────────────────────────────────
def fetch_metric(metric_name: str, tag: Optional[str] = None) -> Optional[float]:
    url = f"{ACTUATOR_BASE}/{metric_name}"
    params = {"tag": tag} if tag else {}
    try:
        resp = requests.get(url, params=params, timeout=0.1)
        resp.raise_for_status()
        return resp.json()["measurements"][0]["value"]
    except Exception:
        return None


def fetch_jvm_heap_mb() -> Optional[float]:
    val = fetch_metric("jvm.memory.used", tag="area:heap")
    return round(val / 1024 / 1024, 1) if val is not None else None


# ── Redis 命中率 ─────────────────────────────────────────────
_redis_hits_prev:   Optional[int] = None
_redis_misses_prev: Optional[int] = None

def fetch_redis_hit_rate() -> Optional[float]:
    global _redis_hits_prev, _redis_misses_prev
    try:
        result = subprocess.run(
            [REDIS_CLI, "INFO", "stats"],
            capture_output=True, text=True, timeout=0.1
        )
        hits = misses = None
        for line in result.stdout.splitlines():
            if line.startswith("keyspace_hits:"):
                hits = int(line.split(":")[1].strip())
            elif line.startswith("keyspace_misses:"):
                misses = int(line.split(":")[1].strip())
        if hits is None or misses is None:
            return None
        if _redis_hits_prev is None:
            _redis_hits_prev, _redis_misses_prev = hits, misses
            return None
        dh = hits   - _redis_hits_prev
        dm = misses - _redis_misses_prev
        _redis_hits_prev, _redis_misses_prev = hits, misses
        total = dh + dm
        return round(dh / total * 100, 1) if total > 0 else 100.0
    except Exception:
        return None


# ── 主循环 ───────────────────────────────────────────────────
def main():
    group = sys.argv[1] if len(sys.argv) > 1 else "unknown"
    write_header = not os.path.exists(OUTPUT_CSV)

    print("=" * 55)
    print(f"  秒杀压测监控启动（每 {INTERVAL_SEC}s 采样，{MAX_DURATION}s 后自动退出）")
    print(f"  group={group}  输出: {OUTPUT_CSV}")
    print("  按 Ctrl+C 提前停止")
    print("=" * 55)

    with open(OUTPUT_CSV, "a", newline="", encoding="utf-8") as f:
        # 检查文件内容是否有 header（不能只判断文件是否存在）
        need_header = True
        if os.path.exists(OUTPUT_CSV) and os.path.getsize(OUTPUT_CSV) > 0:
            with open(OUTPUT_CSV, "r", encoding="utf-8") as rf:
                first_line = rf.readline().strip()
                need_header = not first_line.startswith("timestamp")
        if need_header:
            csv.DictWriter(f, fieldnames=FIELDNAMES).writeheader()

    start_time = time.time()
    try:
        while True:
            elapsed_total = time.time() - start_time
            if elapsed_total >= MAX_DURATION:
                print(f"\n已达到 {MAX_DURATION}s，监控自动退出。")
                break

            ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

            sys_cpu   = fetch_metric("system.cpu.usage")
            proc_cpu  = fetch_metric("process.cpu.usage")
            heap_mb   = fetch_jvm_heap_mb()
            hk_active = fetch_metric("hikaricp.connections.active")
            hk_pend   = fetch_metric("hikaricp.connections.pending")
            redis_hr  = fetch_redis_hit_rate()

            sys_cpu_pct  = round(sys_cpu  * 100, 1) if sys_cpu  is not None and sys_cpu >= 0 else None
            proc_cpu_pct = round(proc_cpu * 100, 1) if proc_cpu is not None and proc_cpu >= 0 else None

            row = {
                "timestamp":          ts,
                "group":              group,
                "system_cpu_pct":     sys_cpu_pct  if sys_cpu_pct  is not None else "",
                "process_cpu_pct":    proc_cpu_pct if proc_cpu_pct is not None else "",
                "jvm_heap_used_mb":   heap_mb      if heap_mb      is not None else "",
                "hikaricp_active":    int(hk_active) if hk_active  is not None else "",
                "hikaricp_pending":   int(hk_pend)   if hk_pend    is not None else "",
                "redis_hit_rate_pct": redis_hr     if redis_hr     is not None else "",
            }

            with open(OUTPUT_CSV, "a", newline="", encoding="utf-8") as f:
                csv.DictWriter(f, fieldnames=FIELDNAMES).writerow(row)

            print(
                f"[{ts}] t={elapsed_total:.1f}s  "
                f"CPU={sys_cpu_pct}%  "
                f"HikariCP={row['hikaricp_active']}  "
                f"Heap={heap_mb}MB  "
                f"Redis命中率={redis_hr}%"
            )

            time.sleep(INTERVAL_SEC)

    except KeyboardInterrupt:
        print("\n监控已停止。")


if __name__ == "__main__":
    main()
