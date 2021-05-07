import requests

r_impfungen = requests.get('https://countee-impfee.b-cdn.net/api/1.1/de/counters/getAll/_iz_sachsen?cached=impfee')
r_impfungen_json = r_impfungen.json()['response']['data']

for impfzentrumid in r_impfungen_json:
    impfzentrum = r_impfungen_json[impfzentrumid]
    anzahl_termine = impfzentrum['counteritems'][0]['val']
    print(f"{impfzentrum['name']} - {anzahl_termine}")
    # Prüfe ob Termine in Dresden
    if (impfzentrum['id'] == 543) & (anzahl_termine > 0):
        print('------------------')
        print('Es gibt Termine!')
        print('------------------')