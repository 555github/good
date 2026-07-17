from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


def read_file(relative_path: str) -> str:
    path = ROOT / relative_path

    if not path.exists():
        raise RuntimeError(f"文件不存在：{relative_path}")

    return path.read_text(encoding="utf-8")


def write_file(relative_path: str, content: str) -> None:
    path = ROOT / relative_path
    path.write_text(content, encoding="utf-8")


def replace_exactly_once(
    relative_path: str,
    old: str,
    new: str,
) -> None:
    content = read_file(relative_path)
    count = content.count(old)

    if count == 0:
        if new in content:
            print(f"已经修复，无需修改：{relative_path}")
            return

        raise RuntimeError(
            f"没有在 {relative_path} 中找到预期代码：\n{old}"
        )

    if count != 1:
        raise RuntimeError(
            f"{relative_path} 中找到 {count} 处匹配，"
            "为避免误改，脚本已停止。"
        )

    write_file(
        relative_path,
        content.replace(old, new, 1),
    )

    print(f"修复完成：{relative_path}")


def ensure_import(
    relative_path: str,
    import_line: str,
    anchor: str,
) -> None:
    content = read_file(relative_path)

    if import_line in content:
        print(f"导入已存在：{relative_path} → {import_line}")
        return

    if anchor not in content:
        raise RuntimeError(
            f"无法在 {relative_path} 中找到导入锚点：{anchor}"
        )

    content = content.replace(
        anchor,
        f"{import_line}\n{anchor}",
        1,
    )

    write_file(relative_path, content)

    print(f"已添加导入：{relative_path} → {import_line}")


def fix_chat_api_client() -> None:
    relative_path = (
        "app/src/main/java/com/example/chatimage/"
        "data/api/ChatApiClient.kt"
    )

    replace_exactly_once(
        relative_path,
        """): ApiOutcome<ChatCompletionResult> =
        withContext(Dispatchers.IO) {""",
        """): ApiOutcome<ChatCompletionResult> =
        withContext<ApiOutcome<ChatCompletionResult>>(
            Dispatchers.IO
        ) {""",
    )


def fix_image_api_client() -> None:
    relative_path = (
        "app/src/main/java/com/example/chatimage/"
        "data/api/ImageApiClient.kt"
    )

    replace_exactly_once(
        relative_path,
        """): ApiOutcome<ImageGenerationResult> =
        withContext(Dispatchers.IO) {""",
        """): ApiOutcome<ImageGenerationResult> =
        withContext<ApiOutcome<ImageGenerationResult>>(
            Dispatchers.IO
        ) {""",
    )


def fix_search_api_client() -> None:
    relative_path = (
        "app/src/main/java/com/example/chatimage/"
        "data/api/SearchApiClient.kt"
    )

    replace_exactly_once(
        relative_path,
        """): ApiOutcome<SearchResponse> =
        withContext(Dispatchers.IO) {""",
        """): ApiOutcome<SearchResponse> =
        withContext<ApiOutcome<SearchResponse>>(
            Dispatchers.IO
        ) {""",
    )


def fix_settings_dialogs() -> None:
    relative_path = (
        "app/src/main/java/com/example/chatimage/"
        "ui/settings/SettingsDialogs.kt"
    )

    ensure_import(
        relative_path=relative_path,
        import_line=(
            "import androidx.compose.foundation.horizontalScroll"
        ),
        anchor=(
            "import androidx.compose.foundation.layout.Arrangement"
        ),
    )


def verify() -> None:
    checks = {
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ChatApiClient.kt"
        ): (
            "withContext<ApiOutcome<ChatCompletionResult>>("
        ),
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ImageApiClient.kt"
        ): (
            "withContext<ApiOutcome<ImageGenerationResult>>("
        ),
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/SearchApiClient.kt"
        ): (
            "withContext<ApiOutcome<SearchResponse>>("
        ),
        (
            "app/src/main/java/com/example/chatimage/"
            "ui/settings/SettingsDialogs.kt"
        ): (
            "import androidx.compose.foundation.horizontalScroll"
        ),
    }

    for relative_path, expected in checks.items():
        content = read_file(relative_path)

        if expected not in content:
            raise RuntimeError(
                f"修复校验失败：{relative_path}\n"
                f"缺少：{expected}"
            )

    print("全部源码修复校验通过。")


def main() -> None:
    print("开始修复 ChatImage v3 源码……")

    fix_chat_api_client()
    fix_image_api_client()
    fix_search_api_client()
    fix_settings_dialogs()
    verify()

    print("ChatImage v3 源码修复完成。")


if __name__ == "__main__":
    main()
