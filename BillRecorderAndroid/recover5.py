import json

path = r'C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl'
longest_content = ""

with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if 'tool_calls' in data:
                for call in data['tool_calls']:
                    if call.get('name') == 'write_to_file':
                        args = call.get('args', {})
                        if 'dialog_add_transaction.xml' in args.get('TargetFile', ''):
                            content = args.get('CodeContent', '')
                            if len(content) > len(longest_content):
                                longest_content = content
                    elif call.get('name') == 'multi_replace_file_content':
                        args = call.get('args', {})
                        if 'dialog_add_transaction.xml' in args.get('TargetFile', ''):
                            chunks_str = args.get('ReplacementChunks', '[]')
                            chunks = chunks_str if isinstance(chunks_str, list) else json.loads(chunks_str)
                            for chunk in chunks:
                                rep = chunk.get('ReplacementContent', '')
                                if len(rep) > len(longest_content):
                                    longest_content = rep
        except Exception as e:
            pass

output_path = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml'
if longest_content:
    with open(output_path, 'w', encoding='utf-8') as out:
        out.write(longest_content)
    print(f"Recovered longest chunk: {len(longest_content)} bytes")
else:
    print("No chunks found")
