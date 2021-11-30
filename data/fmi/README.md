# FMI building map 

To construct the map of the FMI building we started from the OpenStreetMap data.

Unfortuantely, there is a mismatch between the way points are represented in OSM and The-ONE (namely absolute coords versus distance from an abitrary 0,0 point).

For that reason, in this folder there is a small script to adapt that, based on `Osm2Wkt` project, but adapted for our needs.

### Where to get data
I have packed everything inside the `.jmp` file, which you can open with [OpenJUMP](http://www.openjump.org/), which also allows you to edit/updat.

From there, you can right click on a layer, "Save dataset as" and the export the kwt.

### Setup

```bash
pipenv install
pipenv shell
python geo_scaler.py -h # gives you help and example usage
```
