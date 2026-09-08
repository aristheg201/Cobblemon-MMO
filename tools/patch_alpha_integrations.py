from pathlib import Path
p=Path('src/main/java/dev/aristheg/alphaencounter/config/UiConfigManager.java')
s=p.read_text(encoding='utf-8')
old='String id = file.getFileName().toString().replaceFirst("\\.json$", "");'
if old not in s:
    # source currently contains a single Java backslash, which is illegal in a string literal
    old='String id = file.getFileName().toString().replaceFirst("\\.json$", "");'.replace('\\\\.json','\\.json')
new='String fileName = file.getFileName().toString();\n                    String id = fileName.substring(0, fileName.length() - 5);'
if old not in s:
    raise RuntimeError('expected UiConfigManager filename parser not found')
s=s.replace(old,new)
p.write_text(s,encoding='utf-8')
print('Fixed UiConfigManager filename parsing.')
