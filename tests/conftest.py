"""Configuración de pytest: path de importación y aislamiento del entorno."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "scripts"))
sys.path.insert(0, str(RAIZ / "tests"))

# Variables que cambian el comportamiento del generador. Se limpian antes de cada
# test para que el resultado no dependa de cómo esté configurada la máquina: la
# Action las define para su propio uso y, sin esto, contaminaban los tests.
VARIABLES_DEL_GENERADOR = ("DRACAPPS_BUILD_TOOLS", "ANDROID_HOME", "ANDROID_SDK_ROOT")


@pytest.fixture(autouse=True)
def entorno_limpio(monkeypatch):
    for variable in VARIABLES_DEL_GENERADOR:
        monkeypatch.delenv(variable, raising=False)
