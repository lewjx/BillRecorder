import json
import os

path = r'C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl'
with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if 'type' in data and data['type'] == 'TOOL_CALL':
                calls = data.get('tool_calls', [])
                for call in calls:
                    if call.get('name') == 'write_to_file':
                        args = call.get('args', {})
                        if 'dialog_add_transaction.xml' in args.get('TargetFile', ''):
                            with open(r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml', 'w', encoding='utf-8') as out:
                                out.write(args['CodeContent'])
                            print("Recovered from write_to_file")
        except:
            pass
