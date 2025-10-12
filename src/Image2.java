 class Image2 {

    double YColorComponent(double R,double G, double B){
        return .2989*R+.5866*G+.1145*B;
    }
     double C0ColorComponent(double R,double G, double B){
         return 128-.1687*R-.3312*G+.5*B;
     }
     double CGColorComponent(double R,double G, double B){
         return 128+.5*R-.4183*G-.0816*B;
     }

     int[] round(double R,double G, double B){
        return new int [] {(int)Math.round(YColorComponent(R, G, B)),(int)Math.round(C0ColorComponent(R, G, B)),(int)Math.round(CGColorComponent(R, G, B))};
     }
}
