#include "matrix_display.h"
#include "esphome/core/log.h"

namespace esphome {
namespace matrix_display {

static const char *const TAG = "MatrixDisplay";

void MatrixDisplay::setup() {
  ESP_LOGI(TAG, "Setup completed");
}

void MatrixDisplay::loop() {
  if (this->light_ == nullptr)
    return;

  auto *buffer = this->light_->get_buffer();
  if (buffer == nullptr)
    return;

  buffer->all() = Color(0, 0, 0);

  const uint8_t A_pixels[] = {
    1, 2, 5, 6,
    9, 10, 13, 14,
    17, 18, 21, 22,
    25, 26, 27, 28,
    33, 36,
    41, 44,
    49, 52,
    57, 60,
  };

  for (auto i : A_pixels) {
    if (i < buffer->size()) {
      buffer->at(i) = Color(255, 0, 0);
    }
  }

  buffer->schedule_show();
}

}  // namespace matrix_display
}  // namespace esphome