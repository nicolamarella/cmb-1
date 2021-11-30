import re
import math
import pudb
from geopy.distance import distance
from typing import List
from argparse import ArgumentParser, RawDescriptionHelpFormatter
import os.path

matcher = re.compile(r"(LINESTRING|POINT)\s*\((?P<name>.*?)\)")


class Coord:
    def __init__(self, x: float, y: float):
        """ flipping x and y, because reasons, just work """
        self.x = y
        self.y = x

    def __repr__(self):
        return f"{self.x} {self.y}"

    def to_kwt(self):
        return f"{self.x} {self.y}"


class LineString:

    def __init__(self, kind: str, coords: List[Coord]):
        self.coords = coords
        self.kind = kind

    def __repr__(self):
        return f"\nLinestring with coords: {self.coords}"

    def to_kwt(self):
        return f"{self.kind} ("+(", ".join([c.to_kwt() for c in self.coords]))+")\n"


def handle_line(line) -> LineString:
    if line == "\n" or line == "LINESTRING EMPTY\n" or line.startswith("#"):
        return None
    res = matcher.match(line)
    if res is None:
        pudb.set_trace()
    kind = res.groups()[0]
    result = LineString(kind, [])
    number_tuples = res.groups()[1].split(", ")
    assert len(number_tuples) < 6
    for tt in number_tuples:
        numbers = tt.split(" ")
        assert len(numbers) == 2
        result.coords.append(Coord(float(numbers[0]), float(numbers[1])))
    return result


def read_input(file_path) -> List[str]:
    h = open(file_path, "r")
    source = h.readlines()
    h.close()
    return source


def write_output(line_strings: List[LineString], output: str):
    with open(output, "w") as h:
        h.writelines([line.to_kwt() for line in line_strings])


def transform_coord(line_strings: List[LineString]):
    """
    Taken from Osm2Wkt
        // initialize switched to move to value in for loop
        latMin = 90; latMax = -90;
        lonMin = 180; lonMax = -180;
        for(Landmark l : landmarks.values()){
                if(l.latitude < latMin)  latMin = l.latitude;
                if(l.latitude > latMax)  latMax = l.latitude;
                if(l.longitude < lonMin) lonMin = l.longitude;
                if(l.longitude > lonMax) lonMax = l.longitude;	
        }
        System.out.println("found geographic bounds:"
                        + " latitude from " + latMin + " to " + latMax
                        + " longitude from " + lonMin + " to " + lonMax);

        // put coordinate system to upper left corner with (0,0), output in meters
        for(Landmark l : landmarks.values()){
                l.x = geoDistance(l.latitude, l.longitude, l.latitude, lonMin);
                l.y = geoDistance(l.latitude, l.longitude, latMin, l.longitude);
        }

    """
    latMin = float(90)
    latMax = float(-90)
    lonMin = float(180)
    lonMax = float(-180)
    for ls in line_strings:
        for l in ls.coords:
            latMin = min(latMin, l.x)
            latMax = max(latMax, l.x)
            lonMin = min(lonMin, l.y)
            lonMax = max(lonMax, l.y)
    print("found geographic bounds:"
          + " latitude from " + str(latMin) + " to " + str(latMax)
          + " longitude from " + str(lonMin) + " to " + str(lonMax))
    for ls in line_strings:
        for l in ls.coords:
            new_x = geo_distance(l.x, l.y, l.x, lonMin)
            new_y = geo_distance(l.x, l.y, latMin, l.y)
            l.x = new_x
            l.y = new_y


def geo_distance(lat1, lon1, lat2, lon2):
    """
    also taken from osm2wkt
    private double geoDistance(double lat1, double lon1, double lat2, double lon2){
        // return distance between two gps fixes in meters
        double R = 6371; 
        double dLat = Math.toRadians(lat2-lat1);
        double dLon = Math.toRadians(lon2-lon1); 
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * 
        Math.sin(dLon/2) * Math.sin(dLon/2); 
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
        double distance = (R * c * 1000.0d);
        distance = Osm2Wkt.round(distance, Osm2Wkt.precisonFloating);
        return distance;
    """
    R = 6371
    dLat = math.radians(lat2-lat1)
    dLon = math.radians(lon2-lon1)
    a = math.sin(dLat/2) * math.sin(dLat/2) + \
        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
        math.sin(dLon/2) * math.sin(dLon/2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    distance = (R * c * 1000.0)
    return distance


if __name__ == "__main__":
    example_text = '''
Example usage:
    python geo_scaler.py -i osm_wrong_scale/fmi.wkt -o scaled.wkt
    '''
    parser = ArgumentParser(
        description="Simple scaler for WKT generated before using OSM2WKT (absolute coord to distances)",
        epilog=example_text,
        formatter_class=RawDescriptionHelpFormatter
    )
    parser.add_argument("-i", dest="filename", required=True,
                        help="input WKT file ", metavar="FILE")
    parser.add_argument("-o", dest="output", required=True,
                        help="output filename", metavar="FILE")
    args = parser.parse_args()
    lines = read_input(args.filename)
    line_strings = list(filter(lambda x: x is not None, [
        handle_line(line) for line in lines]))
    transform_coord(line_strings)
    write_output(line_strings, args.output)
