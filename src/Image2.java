 class Image {

    double YColorComponent(double R,double G, double B){
        return .2989*R+.5866*G+.1145*B;
    }
     double C0ColorComponent(double R,double G, double B){
         return 128-.1687*R-.3312*G+.5*B;
     }
     double CGColorComponent(double R,double G, double B){
         return 128+.5*R-.4183*G-.0816*B;
     }

     long[] round(double R,double G, double B){
        return new long [] {Math.round(YColorComponent(R, G, B)),Math.round(C0ColorComponent(R, G, B)),Math.round(CGColorComponent(R, G, B))};
     }
}
