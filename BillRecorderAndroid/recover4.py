import json
import os

path = r'C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl'
contents = {}

with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get('source') == 'SYSTEM' and data.get('type') == 'TOOL_RESPONSE':
                content = data.get('content', '')
                if 'dialog_add_transaction.xml' in content and 'File Path:' in content:
                    lines = content.split('\n')
                    for l in lines:
                        if ': ' in l and l.split(': ')[0].isdigit():
                            num = int(l.split(': ')[0])
                            code = l.split(': ', 1)[1]
                            contents[num] = code
        except Exception as e:
            pass

output_path = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml'
if contents:
    with open(output_path, 'w', encoding='utf-8') as out:
        max_line = max(contents.keys())
        for i in range(1, max_line + 1):
            out.write(contents.get(i, '') + '\n')
    print(f"Recovered {len(contents)} lines")
else:
    print("No lines recovered")
