ifndef SUMO_HOME
	export SUMO_HOME := /opt/homebrew/share/sumo/
endif

MEMORY ?= 10G
JAR := network-calibration-SNAPSHOT.jar
sc := java -Xmx$(MEMORY) -cp $(JAR)

scenarios/melbourne/network.osm: scenarios/melbourne/melbourne.osm.pbf
	osmosis --rb file=$< --wx $@

scenarios/melbourne/sumo.net.xml: scenarios/melbourne/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --osm-files $< -o=$@\
	 --proj "+proj=utm +zone=55 +south +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"


scenarios/melbourne/melbourne-from-sumo-network.xml.gz: scenarios/melbourne/sumo.net.xml
	$(sc) org.matsim.application.prepare.network.CreateNetworkFromSumo $<\
	 --target-crs EPSG:32755\
	 --output $@