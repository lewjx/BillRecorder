import json
import re
import os

transcript_path = r"C:\Users\hp\.gemini\antigravity\brain\28ac2f01-8122-46f6-875f-32884be68dc4\.system_generated\logs\transcript.jsonl"
output_path = r"c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml"

os.makedirs(os.path.dirname(output_path), exist_ok=True)

file_content = ""

# 1. Extract base content from step 159
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        if data.get('step_index') == 159:
            content = data.get('content', '')
            lines = content.split('\n')
            xml_lines = []
            for l in lines:
                match = re.match(r'^\d+:(?: (.*))?$', l)
                if match:
                    xml_lines.append(match.group(1) if match.group(1) is not None else "")
            file_content = '\n'.join(xml_lines) + '\n'
            break

# 2. Apply modifications from multi_replace_file_content
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        if 'tool_calls' in data:
            for call in data['tool_calls']:
                if call.get('name') == 'multi_replace_file_content':
                    args = call.get('args', {})
                    if 'dialog_add_transaction.xml' in args.get('TargetFile', ''):
                        chunks_str = args.get('ReplacementChunks', '[]')
                        try:
                            # if it's already a list
                            chunks = chunks_str if isinstance(chunks_str, list) else json.loads(chunks_str)
                            for chunk in chunks:
                                target = chunk.get('TargetContent', '')
                                replacement = chunk.get('ReplacementContent', '')
                                if target:
                                    file_content = file_content.replace(target, replacement)
                        except Exception as e:
                            print("Error parsing chunks:", e)

# 3. Write recovered content
with open(output_path, 'w', encoding='utf-8') as out:
    out.write(file_content)

print(f"Successfully recovered {len(file_content)} bytes to {output_path}")
