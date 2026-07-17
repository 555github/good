from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


NETWORK_FILES = [
    {
        "path": (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ChatApiClient.kt"
        ),
        "result_type": "ChatCompletionResult",
    },
    {
        "path": (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ImageApiClient.kt"
        ),
        "result_type": "ImageGenerationResult",
    },
    {
        "path": (
            "app/src/main/java/com/example/chatimage/"
            "data/api/SearchApiClient.kt"
        ),
        "result_type": "SearchResponse",
    },
]


def read_file(relative_path: str) -> str:
    path = ROOT / relative_path

    if not path.exists():
        raise RuntimeError(
            f"文件不存在：{relative_path}"
        )

    return path.read_text(
        encoding="utf-8"
    )


def write_file(
    relative_path: str,
    content: str,
) -> None:
    path = ROOT / relative_path

    path.write_text(
        content,
        encoding="utf-8",
    )


def add_explicit_with_context_type(
    content: str,
    result_type: str,
    relative_path: str,
) -> str:
    old_variants = [
        (
            f"): ApiOutcome<{result_type}> =\n"
            f"        withContext(Dispatchers.IO) {{"
        ),
        (
            f"): ApiOutcome<{result_type}> =\r\n"
            f"        withContext(Dispatchers.IO) {{"
        ),
    ]

    new_text = (
        f"): ApiOutcome<{result_type}> =\n"
        f"        withContext<ApiOutcome<{result_type}>>(\n"
        f"            Dispatchers.IO\n"
        f"        ) {{"
    )

    already_fixed_markers = [
        (
            f"withContext<ApiOutcome<{result_type}>>(\n"
            f"            Dispatchers.IO\n"
            f"        ) {{"
        ),
        (
            f"withContext<ApiOutcome<{result_type}>>("
            f"Dispatchers.IO"
        ),
    ]

    if any(
        marker in content
        for marker in already_fixed_markers
    ):
        print(
            f"withContext 类型已经存在："
            f"{relative_path}"
        )

        return content

    for old_text in old_variants:
        if old_text in content:
            content = content.replace(
                old_text,
                new_text,
                1,
            )

            print(
                f"已添加 withContext 返回类型："
                f"{relative_path}"
            )

            return content

    raise RuntimeError(
        f"无法在 {relative_path} 中找到函数的 "
        f"withContext 开头。\n"
        f"预期返回类型：ApiOutcome<{result_type}>"
    )


def replace_non_local_returns(
    content: str,
    relative_path: str,
) -> str:
    count = content.count(
        "return@withContext"
    )

    if count == 0:
        print(
            f"没有需要替换的 return@withContext："
            f"{relative_path}"
        )

        return content

    content = content.replace(
        "return@withContext",
        "return@use",
    )

    print(
        f"已将 {relative_path} 中的 "
        f"{count} 个 return@withContext "
        f"替换为 return@use"
    )

    return content


def fix_network_file(
    relative_path: str,
    result_type: str,
) -> None:
    content = read_file(
        relative_path
    )

    content = add_explicit_with_context_type(
        content=content,
        result_type=result_type,
        relative_path=relative_path,
    )

    content = replace_non_local_returns(
        content=content,
        relative_path=relative_path,
    )

    write_file(
        relative_path,
        content,
    )

    verify_network_file(
        relative_path=relative_path,
        result_type=result_type,
    )


def verify_network_file(
    relative_path: str,
    result_type: str,
) -> None:
    content = read_file(
        relative_path
    )

    expected_type = (
        f"withContext<ApiOutcome<{result_type}>>("
    )

    if expected_type not in content:
        raise RuntimeError(
            f"校验失败：{relative_path}\n"
            f"没有找到：{expected_type}"
        )

    if "return@withContext" in content:
        raise RuntimeError(
            f"校验失败：{relative_path} 中仍有 "
            f"return@withContext"
        )

    use_return_count = content.count(
        "return@use"
    )

    print(
        f"校验通过：{relative_path}，"
        f"return@use 数量：{use_return_count}"
    )


def ensure_horizontal_scroll_import() -> None:
    relative_path = (
        "app/src/main/java/com/example/chatimage/"
        "ui/settings/SettingsDialogs.kt"
    )

    content = read_file(
        relative_path
    )

    correct_import = (
        "import androidx.compose.foundation.horizontalScroll"
    )

    wrong_import = (
        "import androidx.compose.foundation.layout.horizontalScroll"
    )

    if wrong_import in content:
        content = content.replace(
            wrong_import,
            correct_import,
        )

        print(
            "已修正错误的 horizontalScroll 导入"
        )

    if correct_import not in content:
        anchors = [
            (
                "import androidx.compose.foundation.layout."
                "Arrangement"
            ),
            (
                "import androidx.compose.foundation.layout."
                "Column"
            ),
        ]

        selected_anchor = next(
            (
                anchor
                for anchor in anchors
                if anchor in content
            ),
            None,
        )

        if selected_anchor is None:
            raise RuntimeError(
                "无法在 SettingsDialogs.kt 中找到"
                "合适的 import 插入位置"
            )

        content = content.replace(
            selected_anchor,
            correct_import
            + "\n"
            + selected_anchor,
            1,
        )

        print(
            "已添加 horizontalScroll 导入"
        )
    else:
        print(
            "horizontalScroll 导入已经存在"
        )

    write_file(
        relative_path,
        content,
    )

    final_content = read_file(
        relative_path
    )

    if correct_import not in final_content:
        raise RuntimeError(
            "horizontalScroll 导入校验失败"
        )


def print_summary() -> None:
    print()
    print("=" * 60)
    print("ChatImage v3 自动修复完成")
    print()
    print("已处理：")

    for item in NETWORK_FILES:
        print(f"- {item['path']}")

    print(
        "- app/src/main/java/com/example/chatimage/"
        "ui/settings/SettingsDialogs.kt"
    )

    print()
    print(
        "关键修复：return@withContext → return@use"
    )
    print("=" * 60)


def main() -> None:
    print(
        "开始修复 ChatImage v3 源码……"
    )

    for item in NETWORK_FILES:
        fix_network_file(
            relative_path=item["path"],
            result_type=item["result_type"],
        )

    ensure_horizontal_scroll_import()
    print_summary()


if __name__ == "__main__":
    main()
