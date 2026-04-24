#include <string>
#include <iostream>
#include <sstream> 

using namespace std;

int evaluateString(int font_size, int text_i) {
	font_size = font_size;
	string text = to_string(text_i);
	int str_size = text.size()*font_size;
	int space_size = (text.size()-1)*(font_size/4);
	return str_size + space_size;
}

int evaluateString_float(int font_size, float text_f, int float_p) {
	stringstream stream; 
	stream.precision(float_p);
	stream << fixed;
	stream << text_f;  
	string text = stream.str();
	int str_size = text.size();
	// if (float_p > 0) {
		// str_size = str_size + 1;
	// }
	str_size = str_size*font_size;
	int space_size = (text.size()-1)*(font_size/4);	
	return str_size + space_size;
}