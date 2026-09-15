#!/usr/bin/env python3
"""由基准测试结果 JSON 生成 README 用的图表。

用法（仓库根目录）：
    python docs/benchmark/make_charts.py

输入：docs/benchmark/ioc-benchmark-result.json
输出：docs/images/benchmark/*.png

图表依赖 matplotlib；中文字体优先用系统里的 Microsoft YaHei / SimHei。
"""
from __future__ import annotations

import json
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RESULT = os.path.join(HERE, "ioc-benchmark-result.json")
OUT_DIR = os.path.join(ROOT, "docs", "images", "benchmark")

# 浅色主题配色
C_BAR = "#4C78A8"
C_BAR2 = "#72B7B2"
C_ACCENT = "#E45756"
C_LINE = "#F58518"
C_IDEAL = "#9E9E9E"
C_GRID = "#DDDDDD"
INK = "#222222"


def setup_fonts() -> None:
    plt.rcParams["font.sans-serif"] = [
        "Microsoft YaHei", "SimHei", "Noto Sans CJK SC", "DejaVu Sans",
    ]
    plt.rcParams["axes.unicode_minus"] = False
    plt.rcParams["figure.facecolor"] = "white"
    plt.rcParams["axes.facecolor"] = "white"
    plt.rcParams["axes.edgecolor"] = "#BBBBBB"
    plt.rcParams["text.color"] = INK
    plt.rcParams["axes.labelcolor"] = INK
    plt.rcParams["xtick.color"] = INK
    plt.rcParams["ytick.color"] = INK


def style(ax) -> None:
    ax.grid(True, axis="x", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)


def human_ops(v: float) -> str:
    if v >= 1e8:
        return f"{v / 1e8:.2f} 亿"
    if v >= 1e4:
        return f"{v / 1e4:,.0f} 万"
    return f"{v:,.0f}"


def chart_throughput(data: dict) -> str:
    rows = data["throughput"]
    names = [r["name"] for r in rows][::-1]
    ops = [r["opsPerSec"] for r in rows][::-1]
    nsop = [r["nsPerOp"] for r in rows][::-1]

    fig, ax = plt.subplots(figsize=(9.2, 4.2), dpi=150)
    y = range(len(names))
    ax.barh(list(y), ops, color=C_BAR, height=0.58)
    for i, (o, n) in enumerate(zip(ops, nsop)):
        ax.text(o * 1.08, i, f"{human_ops(o)} ops/s  ·  {n:,.1f} ns/op",
                va="center", fontsize=9, color=INK)
    ax.set_yticks(list(y))
    ax.set_yticklabels(names, fontsize=10)
    ax.set_xscale("log")
    ax.set_xlim(1e6, 1.2e10)
    ax.set_xlabel("吞吐（ops/s，对数轴）", fontsize=10)
    ax.set_title("单线程稳态吞吐（容器内 13 个 Bean，预热 + 3 轮取最优）",
                 fontsize=12, pad=12)
    style(ax)
    ax.grid(True, axis="x", which="both", color=C_GRID, linewidth=0.8, alpha=0.9)
    fig.tight_layout()
    out = os.path.join(OUT_DIR, "01-throughput.png")
    fig.savefig(out)
    plt.close(fig)
    return out


def chart_concurrency(data: dict) -> str:
    rows = data["concurrency"]
    threads = [r["threads"] for r in rows]
    xlabels = [str(t) for t in threads]
    ops = [r["opsPerSec"] / 1e6 for r in rows]
    speedup = [r["speedup"] for r in rows]
    eff = [r["efficiency"] * 100 for r in rows]

    fig, (ax1, ax2) = plt.subplots(
        2, 1, figsize=(9.2, 6.0), dpi=150, sharex=True,
        gridspec_kw={"height_ratios": [1.25, 1.0]},
        layout="constrained",
    )

    # 上：吞吐
    ax1.bar(xlabels, ops, color=C_BAR, width=0.5)
    for i, o in enumerate(ops):
        ax1.text(i, o + max(ops) * 0.02, f"{o:,.0f}M ops/s", ha="center", fontsize=9, color=INK)
    ax1.set_ylabel("吞吐（百万 ops/s）", fontsize=10)
    ax1.set_ylim(0, max(ops) * 1.16)
    ax1.grid(True, axis="y", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax1.set_axisbelow(True)
    ax1.spines["top"].set_visible(False)
    ax1.spines["right"].set_visible(False)
    ax1.set_title("getBean(Class) 并发扩展性与并行效率（16 核）", fontsize=12, pad=12)

    # 下：加速比 + 并行效率
    ax2.plot(xlabels, speedup, color=C_ACCENT, marker="o", linewidth=2.0, label="实测加速比")
    ax2.plot(xlabels, threads, color=C_IDEAL, linestyle="--", linewidth=1.4, label="理想线性加速")
    for i, (sp, e) in enumerate(zip(speedup, eff)):
        ax2.annotate(f"{sp:.2f}x｜效率 {e:.0f}%", xy=(i, sp), xytext=(0, 11),
                     textcoords="offset points", ha="center", fontsize=9, color=C_ACCENT)
    ax2.set_xlabel("并发线程数", fontsize=10)
    ax2.set_ylabel("加速比", fontsize=10)
    ax2.set_ylim(0, max(threads) * 1.25)
    ax2.grid(True, axis="y", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax2.set_axisbelow(True)
    ax2.spines["top"].set_visible(False)
    ax2.spines["right"].set_visible(False)
    ax2.legend(fontsize=9, frameon=False, loc="upper left")

    out = os.path.join(OUT_DIR, "02-concurrency.png")
    fig.savefig(out)
    plt.close(fig)
    return out


def chart_aop(data: dict) -> str:
    rows = data["aop"]
    labels = [r["label"] for r in rows]
    vals = [r["nsPerOp"] for r in rows]
    colors = [C_BAR2, C_BAR2, C_ACCENT]

    fig, ax = plt.subplots(figsize=(9.2, 4.0), dpi=150)
    bars = ax.bar(labels, vals, color=colors, width=0.5)
    base = vals[1] if len(vals) > 1 else vals[0]
    for i, (b, v) in enumerate(zip(bars, vals)):
        if i == 0:
            extra = ""
        elif i == 1:
            extra = "（基线）"
        else:
            extra = f"（基线的 {v / base:,.1f}x）"
        ax.text(b.get_x() + b.get_width() / 2, v + max(vals) * 0.03,
                f"{v:,.2f} ns/op\n{extra}" if extra else f"{v:,.2f} ns/op",
                ha="center", fontsize=9, color=INK)
    ax.set_ylabel("每次调用耗时（ns/op）", fontsize=10)
    ax.set_ylim(0, max(vals) * 1.3)
    ax.set_title("AOP 调用开销：直接调用 / 容器普通 Bean / JDK 动态代理（@Around 生效）",
                 fontsize=12, pad=12)
    ax.grid(True, axis="y", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    fig.tight_layout()
    out = os.path.join(OUT_DIR, "03-aop-overhead.png")
    fig.savefig(out)
    plt.close(fig)
    return out


def chart_latency(data: dict) -> str:
    rows = data["latency"]
    cats = ["p50", "p90", "p99", "p999"]
    cat_names = ["p50", "p90", "p99", "p99.9"]
    series = [(r["name"], [r[c] for c in cats]) for r in rows]

    fig, ax = plt.subplots(figsize=(9.2, 4.2), dpi=150)
    n = len(series)
    width = 0.36
    xs = list(range(len(cats)))
    palette = [C_BAR, C_ACCENT]
    for i, (name, vals) in enumerate(series):
        offs = [x + (i - (n - 1) / 2) * width for x in xs]
        bars = ax.bar(offs, vals, width=width, label=name, color=palette[i % len(palette)])
        for b, v in zip(bars, vals):
            ax.text(b.get_x() + b.get_width() / 2, v * 1.12, f"{v:,.0f}",
                    ha="center", fontsize=8.5, color=INK)
    ax.set_yscale("log")
    ax.set_ylim(10, 10000)
    ax.set_xticks(xs)
    ax.set_xticklabels(cat_names, fontsize=10)
    ax.set_ylabel("延迟（ns，对数轴）", fontsize=10)
    ax.set_title("getBean(Class) 延迟分位：单线程 vs 8 线程持续压力下（含逐次计时开销）",
                 fontsize=12, pad=12)
    ax.grid(True, axis="y", which="both", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    ax.legend(fontsize=9, frameon=False, loc="upper left")
    out = os.path.join(OUT_DIR, "04-latency.png")
    fig.savefig(out, bbox_inches="tight")
    plt.close(fig)
    return out


def chart_stress_memory(data: dict) -> str:
    stress = data["stress"]
    conc = {r["threads"]: r["opsPerSec"] for r in data["concurrency"]}
    mem = data["memory"]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.4, 4.0), dpi=150,
                                   gridspec_kw={"width_ratios": [1.0, 1.15], "wspace": 0.28})

    # 左：8 线程下「纯 getBean」vs「混合负载（含分配型 API）」
    labels = ["8 线程\n纯 getBean", "8 线程\n混合负载压力"]
    vals = [conc.get(8, 0) / 1e6, stress["opsPerSec"] / 1e6]
    bars = ax1.bar(labels, vals, color=[C_BAR, C_LINE], width=0.5)
    for b, v in zip(bars, vals):
        ax1.text(b.get_x() + b.get_width() / 2, v + max(vals) * 0.02,
                 f"{v:,.0f}M ops/s", ha="center", fontsize=9.5, color=INK)
    drop = (1 - vals[1] / vals[0]) * 100 if vals[0] else 0
    ax1.set_ylabel("吞吐（百万 ops/s）", fontsize=10)
    ax1.set_ylim(0, max(vals) * 1.25)
    ax1.set_title(f"含分配型 API 后吞吐下降 {drop:.0f}%", fontsize=11, pad=10)
    ax1.grid(True, axis="y", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax1.set_axisbelow(True)
    for s in ("top", "right"):
        ax1.spines[s].set_visible(False)

    # 右：压力前后内存增量（KB）
    delta_heap = (mem["afterHeapUsed"] - mem["beforeHeapUsed"]) / 1024
    delta_meta = (mem["afterMetaspaceUsed"] - mem["beforeMetaspaceUsed"]) / 1024
    delta_nonheap = (mem["afterNonHeapUsed"] - mem["beforeNonHeapUsed"]) / 1024
    names = ["GC 后堆", "Metaspace", "非堆"]
    dv = [delta_heap, delta_meta, delta_nonheap]
    bars2 = ax2.barh(names, dv, color=["#54A24B" if v >= 0 else "#E45756" for v in dv], height=0.5)
    for b, v in zip(bars2, dv):
        ax2.text(v + max(abs(x) for x in dv) * 0.04, b.get_y() + b.get_height() / 2,
                 f"+{v:,.0f} KB", va="center", fontsize=9.5, color=INK)
    dclass = mem["afterLoadedClasses"] - mem["beforeLoadedClasses"]
    ax2.set_xlabel("5 秒压力测试前后增量（KB，GC 后读数）", fontsize=10)
    ax2.set_xlim(0, max(abs(x) for x in dv) * 1.42)
    ax2.set_title(f"内存零增长（已加载类 {dclass:+d}）", fontsize=11, pad=10)
    ax2.grid(True, axis="x", color=C_GRID, linewidth=0.8, alpha=0.9)
    ax2.set_axisbelow(True)
    for s in ("top", "right"):
        ax2.spines[s].set_visible(False)

    fig.suptitle(
        f"压力测试：8 线程 × 5 秒 · {stress['totalOps']:,} 次操作 · 错误 {stress['errors']} · "
        f"GC +{stress['gcCountDelta']} 次（+{stress['gcTimeMsDelta']} ms）",
        fontsize=11.5, y=1.02,
    )
    # 该图用 suptitle + wspace 手工布局，不走 tight_layout（否则告警）
    out = os.path.join(OUT_DIR, "05-stress-memory.png")
    fig.savefig(out, bbox_inches="tight")
    plt.close(fig)
    return out


def main() -> int:
    setup_fonts()
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(RESULT, encoding="utf-8") as f:
        data = json.load(f)
    made = [
        chart_throughput(data),
        chart_concurrency(data),
        chart_aop(data),
        chart_latency(data),
        chart_stress_memory(data),
    ]
    meta = data["meta"]
    print(f"环境：{meta['os']}/{meta['osArch']} · JDK {meta['javaVersion']} · {meta['availableProcessors']} 核")
    for p in made:
        print("已生成:", os.path.relpath(p, ROOT).replace("\\", "/"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
