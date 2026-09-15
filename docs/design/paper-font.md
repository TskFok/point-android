# 纸感标题字体

应用将衬线标题字体随 APK 打包，不依赖设备预装中文宋体或运行时字体下载。

- 来源：[Google Fonts 的 Noto Serif SC](https://github.com/google/fonts/tree/main/ofl/notoserifsc)。
- 上游文件：`NotoSerifSC[wght].ttf`；原始 SHA-256 为 `050080d9255a86808f2945bffac582b31ef32bc36411ce29563b4961670c66f9`。
- 许可：SIL Open Font License 1.1，全文随应用保存到 `assets/licenses/NotoSerifSC-OFL.txt`。
- 使用方式：固定字重 600，显示名为 Point Quest Serif，仅用于 display/headline/titleLarge；正文和表单采用系统无衬线。
- 子集：GB2312 常用汉字、JIS X0208 日文汉字、拉丁字母与重音、假名、标点、全角字符以及当前字符串资源。罕见字由 Android 字体回退处理。
- 字体资源约 4.68 MiB（10,918 个字符），构建时不需要额外下载或字体工具。

## 重新生成

在临时 Python 环境安装 `fonttools==4.60.1`，从官方来源取得并校验上游文件，然后执行：

```sh
python scripts/subset-paper-font.py /absolute/path/to/NotoSerifSC.ttf
```

结果为 `app/src/main/res/font/paper_serif_semibold.ttf`。新增非常用标题字时可重新生成；不要将上游 24 MiB 可变字体直接打入 APK。
