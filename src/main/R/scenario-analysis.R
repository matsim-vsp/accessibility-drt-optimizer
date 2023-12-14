library(tidyverse)
library(lubridate)
library(ggpubr)

# Set fleet size
fleet_size <- "520-veh"

# Read data
output_folder <- paste("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark", fleet_size, sep = "/")
departures <- read_delim(paste(output_folder, "/ITERS/it.2/2.legHistogram.txt", sep = ""), delim = "\t")

occupancy_data_520 <- read_delim("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark/520-veh/output_occupancy_time_profiles_drt.txt", delim = "\t")
occupancy_data_410 <- read_delim("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark/410-veh/output_occupancy_time_profiles_drt.txt", delim = "\t")

vehicle_data_350 <- read_tsv("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark/350-veh/output_task_time_profiles_drt.txt") %>%
  mutate(busy =  DRIVE + STOP) %>%
  mutate(free = STAY) %>%
  select(time, busy, free)

vehicle_data_410 <- read_tsv("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark/410-veh/output_task_time_profiles_drt.txt") %>%
  mutate(busy =  DRIVE + STOP) %>%
  mutate(free = STAY) %>%
  select(time, busy, free)

vehicle_data_520 <- read_tsv("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/output/result-analysis/benchmark/520-veh/output_task_time_profiles_drt.txt") %>%
  mutate(busy =  DRIVE + STOP) %>%
  mutate(free = STAY) %>%
  select(time, busy, free)

# Data processing
processed_data_520 <- pivot_longer(data = occupancy_data_520, !time, names_to = "occupancy", values_to = "count")
processed_data_520$occupancy = factor(processed_data_520$occupancy, levels=c("RELOCATE", "STAY", "0 pax", "1 pax", "2 pax", "3 pax", "4 pax", "5 pax", "6 pax", "7 pax", "8 pax"))

processed_data_410 <- pivot_longer(data = occupancy_data_410, !time, names_to = "occupancy", values_to = "count")
processed_data_410$occupancy = factor(processed_data_410$occupancy, levels=c("RELOCATE", "STAY", "0 pax", "1 pax", "2 pax", "3 pax", "4 pax", "5 pax", "6 pax", "7 pax", "8 pax"))

vehicle_data_complete <- bind_rows(
  mutate(vehicle_data_410, fleet_size = "410"),
  mutate(vehicle_data_520, fleet_size = "520")
)

# Making plot
# Occupancy plot
occ_plot_520 <- ggplot(data = processed_data_520, aes(x = time / 3600, y = count, fill = occupancy)) +
  geom_area() +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 520.1)) +
  scale_x_continuous(expand = c(0, 0), limits = c(0, 25)) +
  scale_fill_brewer(palette="Paired") +
  ggtitle("Fleet size = 520") +
  xlab("Time of the day [hour]")+
  ylab("Count")+
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))

occ_plot_410 <- ggplot(data = processed_data_410, aes(x = time / 3600, y = count, fill = occupancy)) +
  geom_area() +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 520.1)) +
  scale_x_continuous(expand = c(0, 0), limits = c(0, 25)) +
  scale_fill_brewer(palette="Paired") +
  ggtitle("Fleet size = 410") +
  xlab("Time of the day [hour]")+
  ylab("Count")+
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))

occ_plot <- ggarrange(occ_plot_520, occ_plot_410, ncol=2, nrow=1, common.legend = TRUE, legend="right")
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/occupancy-comparison.png", plot = occ_plot, width = 2000, height = 1200, units = "px")

# Vehicle plots
busy_vehicle_plot <- ggplot(data = vehicle_data_complete, aes(x = time / 3600, y = busy, color = fleet_size, fill = fleet_size)) +
  geom_area(alpha = 0.5, position = 'identity',size=0.5, colour="black") +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 520)) +
  scale_x_continuous(expand = c(0, 0), limits = c(0, 25)) +
  ggtitle("Number of busy vehicles plot") +
  xlab("Time of the day [hour]")+
  ylab("Count")+
  scale_fill_discrete(name = "fleet size") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/busy_vehicle_plot.png", plot = busy_vehicle_plot, width = 1500, height = 1200, units = "px")
  
# Departures
departures_plot <- ggplot(data = departures, aes(x = time...2 / 3600, y = departures_drt)) +
  geom_line() +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 225)) +
  scale_x_continuous(expand = c(0, 0), limits = c(0, 24)) +
  labs(title = "DRT trips Departures throughout the day",
       x = "Time of the day [hour]",
       y = "Number of departures") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/departures_berlin.png", plot = departures_plot, width = 1500, height = 1200, units = "px")
