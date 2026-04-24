#pragma once

#include "esphome.h"
#include "esphome/components/light/addressable_light.h"

namespace esphome {
namespace matrix_display {

class MatrixDisplay : public Component {
 public:
  void setup() override;
  void loop() override;

  void set_light(light::AddressableLightState* light) { light_ = light; }

 protected:
  light::AddressableLightState* light_;
};

}  // namespace matrix_display
}  // namespace esphome