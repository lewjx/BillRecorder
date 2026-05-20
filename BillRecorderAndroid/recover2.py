import json
import os

path = r'C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl'
with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if 'tool_calls' in data:
                for call in data['tool_calls']:
                    if call.get('name') == 'write_to_file':
                        args = call.get('args', {})
                        target = args.get('TargetFile', '')
                        if 'dialog_add_transaction.xml' in target:
                            with open(r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml', 'w', encoding='utf-8') as out:
                                out.write(args.get('CodeContent', ''))
                            print("Recovered write_to_file")
                    elif call.get('name') == 'multi_replace_file_content':
                        args = call.get('args', {})
                        target = args.get('TargetFile', '')
                        if 'dialog_add_transaction.xml' in target:
                            print("Found multi_replace_file_content")
        except Exception as e:
            pass
