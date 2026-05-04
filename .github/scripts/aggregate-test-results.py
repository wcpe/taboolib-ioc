#!/usr/bin/env python3
"""聚合 JUnit XML 测试结果，生成 Markdown 报告写入 GitHub Step Summary。"""
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(".")
report_path = Path("test-report.md")

rows = []
fail_details = []
total_t = total_f = total_e = total_s = 0
total_time = 0.0

for results_dir in sorted(ROOT.glob("**/build/test-results/test")):
    module = results_dir.relative_to(ROOT).as_posix().replace("/build/test-results/test", "")
    xml_files = sorted(results_dir.glob("TEST-*.xml"))
    if not xml_files:
        continue

    t = f = e = s = 0
    tm = 0.0
    mod_fails = []

    for xml in xml_files:
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        t += int(root.attrib.get("tests", 0))
        f += int(root.attrib.get("failures", 0))
        e += int(root.attrib.get("errors", 0))
        s += int(root.attrib.get("skipped", 0))
        tm += float(root.attrib.get("time", 0))
        suite = root.attrib.get("name", "")
        for tc in root.iter("testcase"):
            for tag in ("failure", "error"):
                node = tc.find(tag)
                if node is not None:
                    msg = (node.attrib.get("message") or "").splitlines()[0][:200]
                    mod_fails.append((f"{suite}.{tc.attrib.get('name')}", tag, msg))

    p = t - f - e - s
    status = "✅" if (f + e) == 0 else "❌"
    rows.append((module, t, p, f, e, s, tm, status))

    total_t += t; total_f += f; total_e += e; total_s += s; total_time += tm

    if mod_fails:
        fail_details.append(f"\n### ❌ `{module}`\n")
        for name, kind, msg in mod_fails:
            fail_details.append(f"- **{name}** `{kind}`\n  > {msg or '（无消息）'}\n")

total_p = total_t - total_f - total_e - total_s
overall = "✅ 全部通过" if (total_f + total_e) == 0 else "❌ 存在失败"

sha = os.environ.get("GITHUB_SHA", "")[:8]
event = os.environ.get("GITHUB_EVENT_NAME", "")
ref = os.environ.get("GITHUB_REF_NAME", "")

lines = [
    "# 🧪 测试报告",
    "",
    f"> Commit: `{sha}` ｜ 触发: `{event}` ｜ 分支: `{ref}`",
    "",
    "| 模块 | 测试数 | 通过 | 失败 | 错误 | 跳过 | 耗时 (s) | 状态 |",
    "|---|---:|---:|---:|---:|---:|---:|:---:|",
]
for module, t, p, f, e, s, tm, status in rows:
    lines.append(f"| `{module}` | {t} | {p} | {f} | {e} | {s} | {tm:.2f} | {status} |")

lines += [
    "",
    "## 📊 汇总",
    "",
    f"- **总计**：{total_t}",
    f"- **通过**：{total_p}",
    f"- **失败**：{total_f}",
    f"- **错误**：{total_e}",
    f"- **跳过**：{total_s}",
    f"- **总耗时**：{total_time:.2f}s",
    f"- **结果**：{overall}",
]

if fail_details:
    lines += ["", "## 失败详情"]
    lines.extend(fail_details)

content = "\n".join(lines) + "\n"
report_path.write_text(content, encoding="utf-8")

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as fp:
        fp.write(content)

print(content)

# 不让脚本本身让 job 失败；由 gradle 退出码决定。
sys.exit(0)
