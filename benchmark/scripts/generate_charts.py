"""
生成论文三张图（秒杀场景版）：
  图6-1: CPU 利用率 + JVM Heap 折线图（对照组 vs 实验组，相对时间轴，[-1s,+2s]窗口）
  图6-2: 上方2个请求分布柱状图（统一坐标轴）+ 下方1个P99双折线对比图
  图6-3: KNN 综合匹配得分雷达图（四维加权分解）

压测场景：1000线程 / ramp-up 1s / 循环1次（瞬时秒杀）
"""

import csv
import os
from datetime import datetime, timedelta

import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm

# ── 中文字体 ──────────────────────────────────────────────────
font_candidates = [
    r'C:\Windows\Fonts\simhei.ttf',
    r'C:\Windows\Fonts\msyh.ttc',
    r'C:\Windows\Fonts\simsun.ttc',
]
font_path = next((p for p in font_candidates if os.path.exists(p)), None)
if font_path:
    prop = fm.FontProperties(fname=font_path)
    plt.rcParams['font.family'] = prop.get_name()
    plt.rcParams['axes.unicode_minus'] = False
else:
    prop = None

BASE       = os.path.dirname(os.path.abspath(__file__))
JMETER_DIR = os.path.join(BASE, '..', 'jmeter-data')
CHARTS_DIR = os.path.join(BASE, '..', 'charts')
os.makedirs(CHARTS_DIR, exist_ok=True)

fp = prop  # 字体简写

# ── 颜色常量 ──────────────────────────────────────────────────
C_EXP  = '#1f77b4'   # 实验组：蓝色
C_CTRL = '#d62728'   # 对照组：红色


# ════════════════════════════════════════════════════════════
# 工具函数
# ════════════════════════════════════════════════════════════

def load_jmeter(path):
    rows = []
    with open(path, encoding='utf-8') as f:
        for r in csv.DictReader(f):
            rows.append({'ts': int(r['timeStamp']), 'elapsed': int(r['elapsed'])})
    return rows


def jmeter_window(path):
    """返回 JMeter 压测的 (t0_ms, t1_ms)"""
    ts = [r['ts'] for r in load_jmeter(path)]
    return min(ts), max(ts)


def build_timeseries_relative(rows, window_ms=100):
    """
    以各自压测起点为 t=0，按 window_ms 窗口统计并发数和 P99。
    返回 (ms_list[], count[], p99[])，时间轴单位为毫秒。
    """
    if not rows:
        return [], [], []
    t0 = min(r['ts'] for r in rows)
    t1 = max(r['ts'] for r in rows)
    ms_list, count_list, p99_list = [], [], []
    t = t0
    while t <= t1:
        bucket = [r['elapsed'] for r in rows if t <= r['ts'] < t + window_ms]
        if bucket:
            ms_list.append(t - t0)
            count_list.append(len(bucket))
            p99_list.append(sorted(bucket)[int(len(bucket) * 0.99)])
        t += window_ms
    return ms_list, count_list, p99_list


def load_monitor_csv(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, encoding='utf-8') as f:
        for r in csv.DictReader(f):
            rows.append(r)
    return rows


def filter_monitor_by_jmeter_window(monitor_rows, group, jmeter_t0_ms, jmeter_t1_ms, pad_sec=5):
    lo = datetime.fromtimestamp(jmeter_t0_ms / 1000) - timedelta(seconds=pad_sec)
    hi = datetime.fromtimestamp(jmeter_t1_ms / 1000) + timedelta(seconds=pad_sec)
    result = []
    for r in monitor_rows:
        if r.get('group', '').strip() != group:
            continue
        ts_str = r['timestamp']
        try:
            ts = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S.%f")
        except ValueError:
            try:
                ts = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
            except ValueError:
                continue
        if lo <= ts <= hi:
            result.append(r)
    return result


# ════════════════════════════════════════════════════════════
# 图6-1: CPU + JVM Heap，[-1s, +2s] 核心窗口，双Y轴
# 图例顺序：实验组CPU → 实验组Heap → 对照组CPU → 对照组Heap
# ════════════════════════════════════════════════════════════

def plot_fig61():
    monitor_path = os.path.join(BASE, 'monitor_metrics.csv')
    all_monitor  = load_monitor_csv(monitor_path)

    exp_jmeter  = os.path.join(JMETER_DIR, 'aggregate_report_exp.csv')
    ctrl_jmeter = os.path.join(JMETER_DIR, 'aggregate_report_ctrl.csv')

    e0, e1 = jmeter_window(exp_jmeter)
    c0, c1 = jmeter_window(ctrl_jmeter)

    # 将 t0 向下取整到秒，与 monitor 0.1s 采样粒度对齐
    # 避免毫秒级偏差（如 271ms）导致飙升点被归入 t<0 区间
    e0_floor = (e0 // 1000) * 1000
    c0_floor = (c0 // 1000) * 1000

    exp_rows  = filter_monitor_by_jmeter_window(all_monitor, 'exp',  e0_floor, e1, pad_sec=5)
    ctrl_rows = filter_monitor_by_jmeter_window(all_monitor, 'ctrl', c0_floor, c1, pad_sec=5)

    if not exp_rows or not ctrl_rows:
        print(f'[WARN] 监控数据不足（exp={len(exp_rows)}, ctrl={len(ctrl_rows)}），跳过图6-1')
        return

    def parse_ts(s):
        for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
            try:
                return datetime.strptime(s, fmt)
            except Exception:
                pass

    def to_series(rows, t0_ms):
        origin = datetime.fromtimestamp(t0_ms / 1000)
        t_list, cpu_list, heap_list = [], [], []
        for r in rows:
            ts = parse_ts(r['timestamp'])
            if ts is None:
                continue
            cpu_s  = r.get('system_cpu_pct', '')
            heap_s = r.get('jvm_heap_used_mb', '')
            if cpu_s == '' or heap_s == '':
                continue
            cpu  = float(cpu_s)
            heap = float(heap_s)
            if cpu < 0 or cpu > 100:
                continue
            t_list.append((ts - origin).total_seconds())
            cpu_list.append(cpu)
            heap_list.append(heap)
        return t_list, cpu_list, heap_list

    def remove_outliers(t_list, cpu_list, heap_list):
        """
        剔除 t<0 区间内的 CPU 断崖异常点：
        若某点 CPU 与前后邻居均值偏差超过 30%，视为孤立噪声点剔除。
        """
        if len(t_list) < 3:
            return t_list, cpu_list, heap_list
        keep = [True] * len(t_list)
        for i in range(1, len(t_list) - 1):
            if t_list[i] >= 0:
                continue  # 只处理 t<0 区间
            neighbors_avg = (cpu_list[i - 1] + cpu_list[i + 1]) / 2
            if abs(cpu_list[i] - neighbors_avg) > 30:
                keep[i] = False
        t_out    = [t_list[i]    for i in range(len(t_list))    if keep[i]]
        cpu_out  = [cpu_list[i]  for i in range(len(cpu_list))  if keep[i]]
        heap_out = [heap_list[i] for i in range(len(heap_list)) if keep[i]]
        return t_out, cpu_out, heap_out

    t_e, cpu_e, heap_e = to_series(exp_rows,  e0_floor)
    t_c, cpu_c, heap_c = to_series(ctrl_rows, c0_floor)
    t_e, cpu_e, heap_e = remove_outliers(t_e, cpu_e, heap_e)
    t_c, cpu_c, heap_c = remove_outliers(t_c, cpu_c, heap_c)

    # 打印 t<0 基线均值，便于验证
    baseline_e = [cpu_e[i] for i in range(len(t_e)) if t_e[i] < 0]
    baseline_c = [cpu_c[i] for i in range(len(t_c)) if t_c[i] < 0]
    if baseline_e:
        print(f'[图6-1] 实验组 t<0 基线 CPU 均值: {sum(baseline_e)/len(baseline_e):.1f}%')
    if baseline_c:
        print(f'[图6-1] 对照组 t<0 基线 CPU 均值: {sum(baseline_c)/len(baseline_c):.1f}%')
    print(f'[图6-1] 实验组 {len(t_e)} 点，对照组 {len(t_c)} 点')

    fig, ax1 = plt.subplots(figsize=(10, 5))
    ax2 = ax1.twinx()

    # 实验组 CPU（左轴，实线，蓝色）
    l1, = ax1.plot(t_e, cpu_e, color=C_EXP, linewidth=2.5,
                   marker='o', markersize=5, label='实验组 CPU（左轴）')
    # 实验组 Heap（右轴，虚线，蓝色）
    l2, = ax2.plot(t_e, heap_e, color=C_EXP, linewidth=1.8, linestyle='--',
                   marker='^', markersize=4, alpha=0.75, label='实验组 Heap（右轴）')
    # 对照组 CPU（左轴，实线，红色）
    l3, = ax1.plot(t_c, cpu_c, color=C_CTRL, linewidth=2.5,
                   marker='s', markersize=5, label='对照组 CPU（左轴）')
    # 对照组 Heap（右轴，虚线，红色）
    l4, = ax2.plot(t_c, heap_c, color=C_CTRL, linewidth=1.8, linestyle='--',
                   marker='D', markersize=4, alpha=0.75, label='对照组 Heap（右轴）')

    # t=0 压测触发线
    ax1.axvline(x=0, color='#555555', linestyle='--', linewidth=1.5, alpha=0.9)
    ax1.text(0.05, 102, '压测触发 t=0', fontproperties=fp, fontsize=8, color='#555555')

    ax1.set_xlabel('相对时间 (s)，t=0 为 JMeter 第一个请求到达时刻', fontproperties=fp)
    ax1.set_ylabel('系统 CPU 利用率 (%)', fontproperties=fp, color='black')
    ax1.set_ylim(0, 110)
    ax1.set_xlim(-1, 2)

    heap_all = heap_e + heap_c
    heap_min = max(0, min(heap_all) - 10) if heap_all else 0
    heap_max = max(heap_all) + 20 if heap_all else 200
    ax2.set_ylabel('JVM Heap 使用量 (MB)', fontproperties=fp, color='gray')
    ax2.set_ylim(heap_min, heap_max)

    # 图例顺序：实验组CPU → 实验组Heap → 对照组CPU → 对照组Heap
    ax1.legend(handles=[l1, l2, l3, l4],
               labels=['实验组 CPU（左轴）', '实验组 Heap（右轴）',
                       '对照组 CPU（左轴）', '对照组 Heap（右轴）'],
               loc='upper right', fontsize=8, prop=fp)
    ax1.grid(True, alpha=0.3)

    plt.title('图6-1  1000并发秒杀压测：CPU 利用率与 JVM Heap 突刺对比\n'
              '（t=0 为压测触发时刻，展示 [-1s, +2s] 核心区间）',
              fontproperties=fp, fontsize=10)
    plt.tight_layout()
    out = os.path.join(CHARTS_DIR, 'fig6-1_cpu_hikaricp.png')
    plt.savefig(out, dpi=150, bbox_inches='tight')
    plt.close()
    print(f'[OK] 图6-1 已保存: {out}')


# ════════════════════════════════════════════════════════════
# 图6-2: 3子图布局
#   上左：实验组请求分布柱状图（X:0-600ms, Y:0-350）
#   上右：对照组请求分布柱状图（X:0-600ms, Y:0-350）
#   下方：实验组+对照组 P99 双折线对比图（X:0-600ms, Y:0-1500ms）
# ════════════════════════════════════════════════════════════

def plot_fig62():
    exp_rows  = load_jmeter(os.path.join(JMETER_DIR, 'aggregate_report_exp.csv'))
    ctrl_rows = load_jmeter(os.path.join(JMETER_DIR, 'aggregate_report_ctrl.csv'))

    t_e_ms, cnt_e, p99_e = build_timeseries_relative(exp_rows,  window_ms=100)
    t_c_ms, cnt_c, p99_c = build_timeseries_relative(ctrl_rows, window_ms=100)

    bar_w = 75

    # 3子图：上方2列 + 下方跨列
    fig = plt.figure(figsize=(13, 9))
    ax_e_bar = fig.add_subplot(2, 2, 1)
    ax_c_bar = fig.add_subplot(2, 2, 2)
    ax_p99   = fig.add_subplot(2, 1, 2)   # 下方跨整行

    # ── 上左：实验组请求分布 ──────────────────────────────────
    ax_e_bar.bar(t_e_ms, cnt_e, width=bar_w, color=C_EXP, alpha=0.85, align='edge')
    ax_e_bar.set_title('实验组（Redis防御）— 请求分布', fontproperties=fp, fontsize=10)
    ax_e_bar.set_ylabel('100ms内请求数', fontproperties=fp)
    ax_e_bar.set_xlabel('相对时间 (ms)', fontproperties=fp)
    ax_e_bar.set_xlim(0, 600)
    ax_e_bar.set_ylim(0, 350)
    ax_e_bar.grid(True, alpha=0.3, axis='y')
    ax_e_bar.text(0.97, 0.95,
                  '写入: 100条（零超卖）',
                  transform=ax_e_bar.transAxes, ha='right', va='top',
                  fontproperties=fp, fontsize=10, color=C_EXP, fontweight='bold',
                  bbox=dict(boxstyle='round,pad=0.4', facecolor='#ddeeff', alpha=0.95))

    # ── 上右：对照组请求分布 ──────────────────────────────────
    ax_c_bar.bar(t_c_ms, cnt_c, width=bar_w, color=C_CTRL, alpha=0.85, align='edge')
    ax_c_bar.set_title('对照组（无防御）— 请求分布', fontproperties=fp, fontsize=10)
    ax_c_bar.set_ylabel('100ms内请求数', fontproperties=fp)
    ax_c_bar.set_xlabel('相对时间 (ms)', fontproperties=fp)
    ax_c_bar.set_xlim(0, 600)
    ax_c_bar.set_ylim(0, 350)
    ax_c_bar.grid(True, alpha=0.3, axis='y')
    ax_c_bar.text(0.97, 0.95,
                  '写入: 109条（超卖9条）',
                  transform=ax_c_bar.transAxes, ha='right', va='top',
                  fontproperties=fp, fontsize=10, color=C_CTRL, fontweight='bold',
                  bbox=dict(boxstyle='round,pad=0.4', facecolor='#ffdddd', alpha=0.95))

    # ── 下方：P99 双折线对比 ──────────────────────────────────
    ax_p99.plot(t_e_ms, p99_e, color=C_EXP, linewidth=2.5,
                marker='o', markersize=6, label='实验组（Redis防御）')
    ax_p99.plot(t_c_ms, p99_c, color=C_CTRL, linewidth=2.5,
                marker='s', markersize=6, label='对照组（无防御）')

    ax_p99.set_title('P99 响应延迟对比（100ms分桶，实验组 vs 对照组）', fontproperties=fp, fontsize=10)
    ax_p99.set_ylabel('P99 响应延迟 (ms)', fontproperties=fp)
    ax_p99.set_xlabel('相对时间 (ms)', fontproperties=fp)
    ax_p99.set_xlim(0, 600)
    ax_p99.set_ylim(0, 1500)
    ax_p99.grid(True, alpha=0.3)

    # P99 峰值标注：标在实际峰值点的数据坐标旁
    if p99_e:
        peak_e     = max(p99_e)
        peak_e_x   = t_e_ms[p99_e.index(peak_e)]
        ax_p99.axhline(peak_e, color=C_EXP, linestyle='--', linewidth=1, alpha=0.6)
        ax_p99.annotate(f'实验组峰值: {peak_e}ms',
                        xy=(peak_e_x, peak_e),
                        xytext=(peak_e_x + 30, peak_e + 60),
                        fontproperties=fp, fontsize=9, color=C_EXP, fontweight='bold',
                        arrowprops=dict(arrowstyle='->', color=C_EXP, lw=1.2))
    if p99_c:
        peak_c     = max(p99_c)
        peak_c_x   = t_c_ms[p99_c.index(peak_c)]
        ax_p99.axhline(peak_c, color=C_CTRL, linestyle='--', linewidth=1, alpha=0.6)
        # 若两峰值 y 坐标接近，将对照组标注偏移到下方避免重叠
        offset_y = -120 if p99_e and abs(peak_c - max(p99_e)) < 200 else 60
        ax_p99.annotate(f'对照组峰值: {peak_c}ms',
                        xy=(peak_c_x, peak_c),
                        xytext=(peak_c_x + 30, peak_c + offset_y),
                        fontproperties=fp, fontsize=9, color=C_CTRL, fontweight='bold',
                        arrowprops=dict(arrowstyle='->', color=C_CTRL, lw=1.2))

    ax_p99.legend(loc='upper right', prop=fp, fontsize=9)

    fig.suptitle('图6-2  1000并发瞬时秒杀压测：请求分布与 P99 响应延迟（100ms分桶）\n'
                 '实验组（Redis+本地消息表防御）vs 对照组（直接写库，无防御）',
                 fontproperties=fp, fontsize=10)
    plt.tight_layout()
    out = os.path.join(CHARTS_DIR, 'fig6-2_tps_p99.png')
    plt.savefig(out, dpi=150, bbox_inches='tight')
    plt.close()
    print(f'[OK] 图6-2 已保存: {out}')


# ════════════════════════════════════════════════════════════
# 图6-3: KNN 雷达图（真实 API 数据）
# ════════════════════════════════════════════════════════════

def plot_fig63():
    volunteers = [
        {
            'name': 'Rank1',
            'similarity': 0.2406,
            'hoursScore': 0.10,
            'creditScore': 0.15,
            'attendanceScore': 0.10,
            'finalScore': 0.5906,
        },
        {
            'name': 'Rank2',
            'similarity': 0.4195,
            'hoursScore': 0.00,
            'creditScore': 0.0125,
            'attendanceScore': 0.00,
            'finalScore': 0.4320,
        },
        {
            'name': 'Rank3',
            'similarity': 0.3222,
            'hoursScore': 0.00,
            'creditScore': 0.00,
            'attendanceScore': 0.00,
            'finalScore': 0.3222,
        },
    ]

    dims     = ['技能相似度\n(TF-IDF)', '服务时长\n加成', '积分信誉\n加成', '出勤率\n加成']
    max_vals = [1.0, 0.10, 0.15, 0.20]
    N        = len(dims)
    angles   = np.linspace(0, 2 * np.pi, N, endpoint=False).tolist()
    angles  += angles[:1]

    fig, ax = plt.subplots(figsize=(7, 6), subplot_kw=dict(polar=True))
    colors  = ['#1f77b4', '#ff7f0e', '#2ca02c']

    for vol, color in zip(volunteers, colors):
        raw    = [vol['similarity'], vol['hoursScore'], vol['creditScore'], vol['attendanceScore']]
        normed = [v / m for v, m in zip(raw, max_vals)]
        normed += normed[:1]
        ax.plot(angles, normed, 'o-', linewidth=2, color=color,
                label=f"{vol['name']}  综合={vol['finalScore']:.4f}")
        ax.fill(angles, normed, alpha=0.12, color=color)

    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(dims, fontproperties=fp, fontsize=9)
    ax.set_ylim(0, 1)
    ax.set_yticks([0.25, 0.5, 0.75, 1.0])
    ax.set_yticklabels(['25%', '50%', '75%', '100%'], fontsize=7)
    ax.grid(True, alpha=0.4)

    ax.legend(loc='upper right', bbox_to_anchor=(1.35, 1.15), prop=fp, fontsize=9)
    plt.title('图6-3  KNN 综合匹配得分雷达图\n（四维加权：技能相似度 + 时长 + 积分 + 出勤率）',
              fontproperties=fp, fontsize=10, pad=20)
    plt.tight_layout()
    out = os.path.join(CHARTS_DIR, 'fig6-3_knn_radar.png')
    plt.savefig(out, dpi=150, bbox_inches='tight')
    plt.close()
    print(f'[OK] 图6-3 已保存: {out}')


if __name__ == '__main__':
    plot_fig61()
    plot_fig62()
    plot_fig63()
    print('\n三张图全部生成完毕，保存在:', CHARTS_DIR)
