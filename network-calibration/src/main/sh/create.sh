#!/bin/bash

module add java/21

jar="network-calibration-SNAPSHOT.jar"

network="melbourne-from-sumo-network-reduced.xml.gz"
ft="melbourne-from-sumo-network-ft.csv.gz"

set -x

java -cp $jar org.matsim.application.prepare.network.params.ApplyNetworkParams freespeed\
  --network $network --input-features $ft --model org.matsim.application.prepare.network.params.ref.GermanyNetworkParams\
  --input-params output-params/20240909-1607/it0462_mae_3.883_rmse_2.094.json\
  --output melbourne-network-from-model.xml.gz

java -cp $jar org.matsim.application.prepare.network.params.ApplyNetworkParams freespeed\
  --network $network --input-features $ft --model org.matsim.application.prepare.network.params.ref.IndividualParams\
  --input-params output-params-indv/20240911-0850/it0501_mae_3.299_rmse_1.691.json\
  --output melbourne-network-modelfree.xml.gz