byte* HSVtoRGB(int H, byte S, byte V) {
	float s = S/100.0;
	float v = V/100.0;
	float Cv = s*v;
	float X = Cv*(1-abs(fmod(H/60.0, 2)-1));
	float m = v-Cv;
	float r,g,b;
	if (H >= 0 && H < 60) {
		r = Cv,g = X,b = 0;
	} else if(H >= 60 && H < 120) {
		r = X,g = Cv,b = 0;
	} else if(H >= 120 && H < 180) {
		r = 0,g = Cv,b = X;
	} else if(H >= 180 && H < 240) {
		r = 0,g = X,b = Cv;
	} else if(H >= 240 && H < 300) {
		r = X,g = 0,b = Cv;
	} else {
		r = Cv,g = 0,b = X;
	}
	byte R = (r+m)*255;
	byte G = (g+m)*255;
	byte B = (b+m)*255;
	byte RGB[3] = {R,G,B};
	return RGB;
}