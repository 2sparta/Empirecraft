#!/usr/bin/env python3
"""Insert latitudinal transition bands between adjacent climate regions.

Each band is a thin polygon straddling the shared latitude edge of two climate
regions (or one region and the latitude fallback), carrying the averaged climate
values of its neighbours. Together with the interpolation in Geography.climate
this turns every hard climate line into a realistic stepped gradient, and the
warped-lattice smoothing widens it into a blur instead of a jump.
Idempotent: re-running replaces bands by id.
"""
import json
from pathlib import Path

DEFAULTS = Path(__file__).resolve().parent / "src/main/resources/earth-defaults.json"

# (id, upper region or None for latitude fallback, lower region or None,
#  lon_min, lon_max, lat_high, lat_low, tilt_c)
# tilt_c: west-to-east temperature step across the band (coast vs inland asymmetry).
BANDS = [
    ("boreal_transition",        "western_europe", None,                -11.0, 60.0, 61.0, 57.0, -0.8),
    ("south_europe_transition",  "western_europe", "mediterranean",     -10.0, 15.0, 45.0, 43.0, 0.8),
    ("pontic_transition",        "ukraine_continental", "mediterranean", 22.0, 37.0, 45.0, 43.0, 0.4),
    ("sahara_north_transition",  "mediterranean",  "sahara",            -17.0, 35.0, 31.5, 28.5, 0.8),
    ("sahara_south_transition",  None,             "sahara",            -17.0, 35.0, 16.5, 13.5, -0.4),
    ("arabia_north_transition",  None,             "arabia",             35.0, 60.0, 34.0, 31.0, 0.4),
    ("siberia_south_transition", "siberia",        None,                 60.0, 140.0, 52.0, 48.0, -0.6),
    ("prairie_south_transition", "north_america_temperate", None,      -100.0, -65.0, 37.0, 33.0, 0.4),
]
FALLBACK_MEAN = lambda lat: max(-28, 27 - abs(lat) * 0.57)
FALLBACK_AMP = lambda lat: min(24, abs(lat) * 0.34)
FALLBACK_RAIN = lambda lat: 2200 if abs(lat) < 12 else 1100 if abs(lat) < 25 else 350 if abs(lat) < 35 else 750 if abs(lat) < 60 else 300


def midpoint(upper, lower, lat):
    """Average the climate values of the two neighbouring regions."""
    def value(feature, key, fallback):
        return feature["values"].get(key, fallback) if feature else fallback
    fields = {
        "mean_c": (value(upper, "mean_c", FALLBACK_MEAN(lat)) + value(lower, "mean_c", FALLBACK_MEAN(lat))) / 2,
        "amplitude_c": (value(upper, "amplitude_c", FALLBACK_AMP(lat)) + value(lower, "amplitude_c", FALLBACK_AMP(lat))) / 2,
        "annual_rain_mm": (value(upper, "annual_rain_mm", FALLBACK_RAIN(lat)) + value(lower, "annual_rain_mm", FALLBACK_RAIN(lat))) / 2,
    }
    months = [f["values"].get("monthly_rain_mm") for f in (upper, lower) if f and "monthly_rain_mm" in f["values"]]
    if len(months) == 2:
        fields["monthly_rain_mm"] = [(a + b) / 2 for a, b in zip(months[0], months[1])]
    return {key: (v if isinstance(v, list) else round(v, 1)) for key, v in fields.items()}


def main():
    data = json.loads(DEFAULTS.read_text())
    features = data["features"]
    regions = {f["id"]: f for f in features if f["layer"] == "climate"}
    count = 0
    for band_id, upper, lower, lon_min, lon_max, lat_high, lat_low, tilt in BANDS:
        lat_mid = (lat_high + lat_low) / 2
        base = midpoint(regions.get(upper), regions.get(lower), lat_mid)
        # Split into three longitude segments with a stepped temperature tilt so the
        # transition itself varies along its length (coastal west, inland east).
        span = (lon_max - lon_min) / 3
        for seg in range(3):
            seg_lon_min = lon_min + seg * span
            seg_lon_max = seg_lon_min + span
            values = dict(base)
            values["mean_c"] = round(base["mean_c"] + tilt * (seg - 1), 1)
            ring = [[seg_lon_min, lat_high], [seg_lon_max, lat_high], [seg_lon_max, lat_low], [seg_lon_min, lat_low], [seg_lon_min, lat_high]]
            seg_id = band_id + ("_west", "_mid", "_east")[seg]
            band = {"id": seg_id, "layer": "climate", "label": seg_id.replace("_", " "),
                    "priority": 12, "rings": [ring], "values": values}
            features[:] = [f for f in features if f["id"] != seg_id and f["id"] != band_id]
            features.append(band)
            count += 1
    DEFAULTS.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
    print(f"WROTE {count} climate band segments")


if __name__ == "__main__":
    main()
