library(tidyverse)

## Read data
# Fixed threshold
th_0 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-0.0.tsv", 
                   delim = "\t")
th_2 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-0.2.tsv", 
                   delim = "\t")
th_4 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-0.4.tsv", 
                   delim = "\t")
th_6 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-0.6.tsv", 
                   delim = "\t")
th_8 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-0.8.tsv", 
                   delim = "\t")
th_10 <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/threshold-1.0.tsv", 
                    delim = "\t")
passive <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/hard-constraint/threshold-0.tsv",
                      delim = "\t")

# Dynamic threshold
veh_300 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-300.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_350 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-350.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_400 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-400.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_410 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-410.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_420 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-420.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_430 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-430.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_440 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-440.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_450 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-450.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_500 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-500.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_550 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-550.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)
veh_600 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/fleet-600.tsv") %>% 
  mutate(iteration = row_number() - 1) %>%
  relocate(iteration, .before = fleet_size)

## Data processing
complete_fixed_threshold_data <- bind_rows(
  mutate(th_0, threshold = "benchmark"),
  mutate(th_2, threshold = "0.2"),
  mutate(th_4, threshold = "0.4"),
  mutate(th_6, threshold = "0.6"),
  mutate(th_8, threshold = "0.8"),
  mutate(th_10, threshold = "1"),
  mutate(passive, threshold = "passive")
) %>% relocate(threshold, .before = fleet_size)

complete_dynamic_data <- bind_rows(veh_300, veh_350, veh_400, veh_410, veh_420, veh_430, veh_440, veh_450, veh_500, veh_550, veh_600)

max_rows <- complete_dynamic_data %>% 
  group_by(fleet_size) %>%
  slice(which.max(satisfactory_rate))

processed_data <- bind_rows(
  max_rows %>%
    select(-iteration) %>%
    mutate(threshold = "dynamic") %>%
    relocate(threshold, .before = fleet_size),
  complete_fixed_threshold_data
)

table_summary <- processed_data %>%
  filter (satisfactory_rate > 0.95) %>%
  group_by(threshold) %>%
  slice(which.min(fleet_size)) %>%
  mutate(request_per_vehicle = num_drt_trips_served / fleet_size)

table_summary_2 <- processed_data %>%
  filter (fleet_size == 550) %>%
  mutate(request_per_vehicle = num_drt_trips_served / fleet_size)

max_work_load_data <- processed_data %>% 
  filter(threshold == "benchmark" | threshold == "dynamic" | threshold == 0.8 | threshold == 1) %>% 
  filter (satisfactory_rate > 0.95)
#group_by(threshold) %>%
#slice(which.min(fleet_size))

## General Plots
satisfacotry_plot <- ggplot(data = processed_data %>% 
                                 filter(threshold == "benchmark" | threshold == "dynamic" | threshold == 0.8 | threshold == 1), 
                               aes(x = fleet_size, y = satisfactory_rate, color = threshold, shape = threshold)) +
  geom_line()+
  #geom_point() +
  geom_hline(yintercept = 0.95, linetype = "dashed", color = "black") +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 1)) +
  xlim(300, 600) +
  labs(title = "Overall trip satisfactory rate",
       x = "Fleet size",
       y = "Proportion of satisfactory trips") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/satisfactory_rate_berlin.png", plot = satisfacotry_plot, width = 1500, height = 1200, units = "px")

system_travel_time_plot <- ggplot(data = processed_data %>% 
                                       filter(threshold == "benchmark" | threshold == "dynamic" | threshold == 0.8 | threshold == 1), 
                                     aes(x = fleet_size, y = system_total_travel_time, color = threshold, shape = threshold)) +
  geom_line()+
  #geom_point() +
  geom_hline(yintercept = 0.95, linetype = "dashed", color = "black") +
  scale_y_continuous(expand = c(0, 0), limits = c(3e7, 5e7)) +
  xlim(300, 600) +
  labs(title = "System total travel time",
       x = "Fleet size",
       y = "System total travel time [s]") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/system_travel_time_berlin.png", plot = system_travel_time_plot, width = 1500, height = 1200, units = "px")


workload_plot <- ggplot(data = max_work_load_data, aes(x = num_drt_trips_served / fleet_size, y = system_total_travel_time, color = threshold, shape = threshold)) +
  geom_line() +
  # geom_point() +
  labs(title = "Vehicle workload and system total travel time",
       x = "Average requests served per vehicle [# requests / day]",
       y = "System total travel time [s]") +
  theme_light() +
  #scale_y_continuous(expand = c(0, 0), limits = c(3e7, 4e7)) +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/workload_plot_berlin.png", plot = workload_plot, width = 1500, height = 1200, units = "px")


num_requests_plot <- ggplot(data = processed_data %>%
                              filter(threshold == "benchmark" | threshold == "dynamic" | threshold == 0.8 | threshold == 1) #%>% filter(satisfactory_rate > 0.95)
                              , aes(x = fleet_size, y = num_drt_trips_served, color = threshold, shape = threshold)) +
  geom_line() +
  # geom_point() +
  labs(title = "Number of DRT trips served under different setups",
       x = "Fleet size",
       y = "Number of DRT trips served") +
  theme_light() +
  xlim(300,600) +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 26000)) +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/num_requests_berlin.png", plot = num_requests_plot, width = 1500, height = 1200, units = "px")


# Iteration summary
iteration_summary <- ggplot(complete_dynamic_data %>% filter(fleet_size == 300 | fleet_size == 400 | fleet_size == 500 | fleet_size == 600), aes(x = iteration, y = satisfactory_rate, color = as.character(fleet_size))) +
  geom_line() +
  geom_hline(yintercept = 0.95, linetype = "dashed", color = "black") + 
  scale_color_discrete(name = "fleet size") +
  ylim(0,1) +
  labs(title = "Dynamic threshold: iterative approach",
       x = "Iteration",
       y = "Proportion of satisfactory trips") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/iteration-summary-berlin.png", plot = iteration_summary, width = 1500, height = 1200, units = "px")

fleet_300_it_116 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-300/it-116.tsv")
fleet_400_it_144 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-400/it-144.tsv")
fleet_410_it_92 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-410/it-92.tsv")
fleet_420_it_149 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-420/it-149.tsv")
fleet_430_it_34 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-430/it-34.tsv")
fleet_440_it_38 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-440/it-38.tsv")
fleet_450_it_40 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-450/it-40.tsv")
fleet_500_it_139 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-500/it-139.tsv")
fleet_550_it_167 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-550/it-167.tsv")
fleet_600_it_153 <- read_tsv("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/data/soft-constraint/dynamic-threhold/LR-0.05/daily-data/fleet-600/it-153.tsv")

## Daily dynamic threshold illustration
dynamic_threshold_plot <- ggplot() +
  geom_line(data = fleet_300_it_116, aes(x = time / 3600, y = threshold, color = "300")) +
  geom_line(data = fleet_400_it_144, aes(x = time / 3600, y = threshold, color = "400")) +
  geom_line(data = fleet_500_it_139, aes(x = time / 3600, y = threshold, color = "500")) +
  geom_line(data = fleet_600_it_153, aes(x = time / 3600, y = threshold, color = "600")) +
  xlim(0, 24) +
  ylim(0, 1) +
  #scale_color_manual(name = "fleet size", values=c("red", "orange", "blue", "purple")) +
  scale_color_discrete(name = "fleet size") +
  labs(title = "Dynamic threshold",
       x = "Time of the day [hour]",
       y = "Dynamic threshold value") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/dynamic-gamma-berlin.png", plot = dynamic_threshold_plot, width = 1500, height = 1200, units = "px")

############## Other plots ########################
workload_fleet_size_plot <- ggplot(processed_data %>% 
                          filter(threshold == "benchmark" | threshold == "dynamic" | threshold == 0.8 | threshold == 1) %>%
                          filter(satisfactory_rate > 0.95),
                        aes(x = fleet_size, y = num_drt_trips_served / fleet_size, color = threshold, shape = threshold))+
  geom_line() + 
  #geom_point() +
  scale_y_continuous(expand = c(0, 0), limits = c(0, 55)) +
  xlim(300,600) +
  labs(title = "System total travel time against average vehicle workload",
       x = "Fleet size",
       y = "Average requests served per vehicle [# requests / day]") +
  theme_light() +
  theme(plot.title = element_text(hjust = 0.5))
ggsave("/Users/luchengqi/Documents/TU-Berlin/Projects/DRT-accessbility-study/r-scipts/plots/workload_fleet_size_plot_berlin.png", plot = workload_fleet_size_plot, width = 1500, height = 1200, units = "px")


