import re
html = open('sipcfg.html', encoding='utf-8', errors='ignore').read()
fields = {}
for m in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*>', html):
    tag = m.group(0)
    name = m.group(1)
    vm = re.search(r'value="([^"]*)"', tag)
    val = vm.group(1) if vm else ''
    typ = re.search(r'type="([^"]+)"', tag)
    t = typ.group(1) if typ else 'text'
    if t in ('radio', 'checkbox'):
        if 'checked' in tag:
            fields[name] = val
    elif t not in ('submit', 'button', 'reset', 'file'):
        if name not in fields:
            fields[name] = val
for m in re.finditer(r'<select[^>]*name="([^"]+)"[^>]*>(.*?)</select>', html, re.DOTALL):
    name, inner = m.group(1), m.group(2)
    sel = re.search(r'<option[^>]*value="([^"]*)"[^>]*selected', inner)
    if sel:
        fields[name] = sel.group(1)
    else:
        first = re.search(r'<option[^>]*value="([^"]*)"', inner)
        if first:
            fields[name] = first.group(1)
print('TOTAL_FIELDS:' + str(len(fields)))
keys_of_interest = [k for k in sorted(fields) if any(
    x in k for x in ['SipPxy', 'SessionTimer', '100rel', 'AllowSame',
                     'ServerCheck', 'SameLocal', 'OutBound'])]
for k in keys_of_interest:
    print(k + '=' + fields[k])
# Save all fields for POST
with open('sipcfg_fields.txt', 'w') as f:
    for k in sorted(fields):
        f.write(k + '=' + fields[k] + '\n')
