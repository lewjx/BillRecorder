import csv

dbs = 0.0
ocbc = 0.0

with open("export_20_05_26_139.csv", "r") as f:
    reader = csv.reader(f)
    next(reader)
    for row in reader:
        if len(row) < 5: continue
        is_inc = "(+)" in row[1]
        amt = float(row[2])
        acc = row[4]
        if acc == "dbs":
            dbs += amt if is_inc else -amt
        elif acc == "ocbc":
            ocbc += amt if is_inc else -amt

print("dbs:", dbs)
print("ocbc:", ocbc)
