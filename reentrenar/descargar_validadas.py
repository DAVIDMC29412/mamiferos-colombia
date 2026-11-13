"""
Descarga las fotos VALIDADAS en el panel y las ordena en carpetas por especie,
listas para agregarlas al dataset y reentrenar el modelo en Kaggle.

Uso:
    python descargar_validadas.py fotos_validadas.csv nuevas_fotos

Resultado:
    nuevas_fotos/<especie>/<id>.jpg      (una carpeta por especie, como 04_dataset)
    nuevas_fotos/metadata_nuevas.csv     (los datos de cada foto)
Las especies nuevas (que no están entre las 40) quedan en nuevas_fotos/_nuevas/<nombre>/.
"""
import csv
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path


def carpeta_segura(texto: str) -> str:
    t = unicodedata.normalize("NFD", texto.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "_", t).strip("_") or "sin_nombre"


def main(csv_entrada: str, salida: str) -> None:
    raiz = Path(salida)
    raiz.mkdir(parents=True, exist_ok=True)
    filas = list(csv.DictReader(open(csv_entrada, encoding="utf-8-sig")))
    ok = 0
    for f in filas:
        if f.get("especieValidadaId"):
            destino = raiz / f["especieValidadaId"]
        else:
            destino = raiz / "_nuevas" / carpeta_segura(f.get("especieValidada", ""))
        destino.mkdir(parents=True, exist_ok=True)
        archivo = destino / f"{f['id']}.jpg"
        if archivo.exists():
            ok += 1
            continue
        try:
            urllib.request.urlretrieve(f["fotoUrl"], archivo)
            ok += 1
        except Exception as e:
            print("No se pudo descargar", f["id"], e)
    with open(raiz / "metadata_nuevas.csv", "w", newline="", encoding="utf-8") as s:
        w = csv.DictWriter(s, fieldnames=filas[0].keys() if filas else ["id"])
        w.writeheader()
        w.writerows(filas)
    print(f"Listo: {ok} de {len(filas)} fotos en {raiz.resolve()}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
