#include <Arduino.h>
#include <ADS1115_WE.h>
#include <Wire.h>

#include <TimerOne.h>

#define I2C_ADDRESS 0x48
int interruptPin = 2;
int buzzerPin = 3;
volatile bool convReady = false;
volatile bool timerFlag = false;

void convReadyAlert();
void timerIsr();

ADS1115_WE adc = ADS1115_WE(I2C_ADDRESS);

#define STATE_STOP 'P'
#define STATE_SEND 'H'
#define BEEP_ON 'A'
#define BEEP_DURATION 200
#define BEEP_FREQUENCY 1000

#define DEFAULT_STATE STATE_STOP

char state = DEFAULT_STATE;

void setup()
{
    Wire.begin();
    Serial.begin(115200);
    pinMode(interruptPin, INPUT_PULLUP);
    pinMode(buzzerPin, OUTPUT);
    if (!adc.init())
    {
        Serial.println("ADS1115 not connected!");
    }

    adc.setVoltageRange_mV(ADS1115_RANGE_2048);  // comment line/change parameter to change range
    adc.setCompareChannels(ADS1115_COMP_0_1);    // comment line/change parameter to change channels
    adc.setAlertPinMode(ADS1115_ASSERT_AFTER_1); // needed in this sketch to enable alert pin (doesn't matter if you choose after 1,2 or 4)
    adc.setConvRate(ADS1115_860_SPS);            // comment line/change parameter to change SPS
    adc.setAlertPinToConversionReady();          // needed for this sketch
    attachInterrupt(digitalPinToInterrupt(interruptPin), convReadyAlert, FALLING);
    adc.setMeasureMode(ADS1115_CONTINUOUS); // the conversion ready alert pin also works in continuous mode
    // use timer to send data exactly every 2 ms
    Timer1.initialize(2000);
}

int32_t result = 0;
int16_t last_result = 0;
uint16_t count = 0;

unsigned long last_beep_time = 0;
bool beep_on = false;

void loop()
{
    // get new state if available
    if (Serial.available() > 0)
    {
        char c = Serial.read();
        if (c == BEEP_ON)
        {
            // high for .5 seconds on pin 3
            digitalWrite(buzzerPin, HIGH);
            beep_on = true;
            last_beep_time = millis();
        }
        else if (c == STATE_STOP || c == STATE_SEND)
        {
            if (c != state) // cambio de estado
            {
                state = c;
                if (state == STATE_STOP)
                {
                    adc.setMeasureMode(ADS1115_SINGLE);
                    // disable timer interrupt
                    Timer1.detachInterrupt();
                }
                else
                {
                    // enable timer interrupt
                    Timer1.attachInterrupt(timerIsr);
                    adc.setMeasureMode(ADS1115_CONTINUOUS); // the conversion ready alert pin also works in continuous mode
                    adc.startSingleMeasurement();
                }
            }
        }
    }

    if (beep_on)
    {
        if (millis() - last_beep_time > BEEP_DURATION)
        {
            digitalWrite(buzzerPin, LOW);
            beep_on = false;
        }
    }

    if (convReady)
    {
        convReady = false;
        result += adc.getRawResult();
        count++;
    }

    if (timerFlag)
    {
        last_result = result / count;
        Serial.println(last_result);
        result = 0;
        count = 0;
        timerFlag = false;
    }
}

void timerIsr()
{
    timerFlag = true;
}

void convReadyAlert()
{
    convReady = true;
}