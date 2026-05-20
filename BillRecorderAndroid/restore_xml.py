import shutil

src = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\build\intermediates\incremental\release\mergeReleaseResources\stripped.dir\layout\dialog_add_transaction.xml'
dst = r'c:\Users\hp\Downloads\BillRecorder\BillRecorder\BillRecorderAndroid\app\src\main\res\layout\dialog_add_transaction.xml'

shutil.copy2(src, dst)
print("File successfully restored!")
