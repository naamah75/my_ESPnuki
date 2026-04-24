#include <string>
#include <iostream>

std::string generateDateFormat(esphome::time::ESPTime time) {
  std::string months_abbreviated[12] = {"Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic"};
  std::string months[12] = {"Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"};
  std::string weekdays_abbreviated[7] = {"Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab"};
  std::string weekdays_unicode[7] = {"Domenica", "Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"};
  std::string weekdays[7] = {"Domenica", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato"};
  std::string dateFormat = weekdays_unicode[atoi(time.strftime("%w").c_str())] + " " + time.strftime("%d").c_str() + " " + months[atoi(time.strftime("%m").c_str()) - 1] + " " + time.strftime("%Y").c_str();
  return dateFormat;
}

std::string monthsToItalian(esphome::time::ESPTime time, bool abbreviated) {
  std::string months_abbreviated[12] = {"Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic"};
  std::string months[12] = {"Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"};
  std::string dateFormat;
  if (abbreviated) {
    dateFormat = months_abbreviated[atoi(time.strftime("%m").c_str()) - 1];
  } else {
    dateFormat = months[atoi(time.strftime("%m").c_str()) - 1];
  }
  return dateFormat;
}

std::string weekdaysToItalian(esphome::time::ESPTime time, bool abbreviated, bool unicode) {
  std::string weekdays_abbreviated[7] = {"Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab"};
  std::string weekdays_unicode[7] = {"Domenica", "Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"};
  std::string weekdays[7] = {"Domenica", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato"};
  std::string dateFormat;
  if (abbreviated) {
    dateFormat = weekdays_abbreviated[atoi(time.strftime("%w").c_str())];
  } else {
    if (unicode) {
        dateFormat = weekdays_unicode[atoi(time.strftime("%w").c_str())];
    } else {
		dateFormat = weekdays[atoi(time.strftime("%w").c_str())];
    }
  }  
  return dateFormat;
}

std::string locationToItalian(std::string location) {
  if (location == "home") return "In casa";
  if (location == "not_home") return "Fuori casa";
  return location;
}
