import json
import os

path = r'C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl'
contents = {}

with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get('type') == 'TOOL_CALL':
                calls = data.get('tool_calls', [])
                for call in calls:
                    if call.get('name') == 'view_file':
                        args = call.get('args', {})
                        target = args.get('AbsolutePath', '')
                        if 'dialog_add_transaction.xml' in target:
                            # The file wasn't fully viewed in one go, but let's check
                            pass
            elif data.get('type') == 'VIEW_FILE' or (data.get('type') == 'PLANNER_RESPONSE' and 'VIEW_FILE' in data.get('content', '')):
                # wait, view_file responses are usually in the next step with source SYSTEM and type TOOL_RESPONSE?
                pass
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

if contents:
    with open(r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml', 'w', encoding='utf-8') as out:
        max_line = max(contents.keys())
        for i in range(1, max_line + 1):
            out.write(contents.get(i, '') + '\n')
    print(f"Recovered {len(contents)} lines")
else:
    print("No lines recovered")
