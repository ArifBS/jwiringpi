package jwiringpi;

public interface JWiringPiPWM {
    public void pwmSetMode (int pin, int value);
    public void pwmSetRange(int pin,int range) ;
    public void pwmSetClock(int pin,int divisor) ;
}
