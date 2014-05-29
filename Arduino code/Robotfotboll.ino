#include <TimerThree.h>

#include <Max3421e.h>
#include <Usb.h>
#include <Servo.h>
#include <AndroidAccessory.h>

//###################################################################################
//##### Defines for Communication bytes
//###################################################################################
#define COMMAND_TEXT 0xF
#define TARGET_DEFAULT 0xF
#define SEARCH_BALL 0x01
#define SEARCH_GOAL 0x02
#define BIT_LOCATE_OBJECT 0x03
#define BIT_OBJECT_CENTER 0x04
#define BIT_OBJECT_CENTER_LEFT 0x05
#define BIT_OBJECT_CENTER_RIGHT 0x06
#define BIT_OBJECT_RIGHT 0x08
#define BIT_OBJECT_LEFT 0x07
#define BIT_SHOOT_BALL 0x0A
#define OBJECT_BALL_GRAB 0x09
#define BIT_REVERSE 0x0B


#define FULL_ROTATION 1278
#define LOOP_DELAY 1

//###################################################################################
//##### Define for PhotoPins
//###################################################################################
#define PHOTO_PIN 0

//###################################################################################
//##### Defines for ServoPins
//###################################################################################
#define SERVO_PIN 2
#define SERVO_UP_POS 120
#define SERVO_DOWN_POS 93
#define SERVO_SHOOT 135

//###################################################################################
//##### Defines for SensorPins
//###################################################################################

#define LEFT_ECHO 6
#define LEFT_TRIG 7

#define RIGHT_ECHO 8
#define RIGHT_TRIG 9

#define BACK_ECHO 10
#define BACK_TRIG 11

#define COLLISION_THRESHOLD 4
#define DISTANCE_TO_BALL 12

//###################################################################################
//##### Defines for collision interrupt
//###################################################################################

#define COLLISION_PIN 3

//###################################################################################
//##### Defines for MotorPins
//###################################################################################
#define Motor_Right 1
#define Motor_Left  2 
#define Motor_Back  3

#define Motor_R_Dir 23                      //Direction Pin - Initial State is ZERO
#define Motor_R_Step 22                     //Step Pin - Pulse this to step the motor in the direction selected by the Direction Pin
#define Motor_R_MS1 29
#define Motor_R_MS2 27
#define Motor_R_MS3 25

#define Motor_B_Dir 31                      //Direction Pin - Initial State is ZERO
#define Motor_B_Step 33
#define Motor_B_MS1 39
#define Motor_B_MS2 37
#define Motor_B_MS3 35

#define Motor_L_Dir 41                      //Direction Pin - Initial State is ZERO
#define Motor_L_Step 43
#define Motor_L_MS1 49
#define Motor_L_MS2 47
#define Motor_L_MS3 45


//###################################################################################
//##### Defines for Speeds
//###################################################################################
#define FORWARD 0
#define BACKWARD 1
#define LEFT 0
#define RIGHT 1
#define FullStep 0x01
#define HalfStep 0x02
#define QuarterStep 0x04
#define EightStep 0x08
#define SixteenthStep 0x16


AndroidAccessory acc("Robotfotboll", "Sir_Hattrick", "Description",
"1.0", "URI", "Serial");

byte rcvmsg[4];
byte sntmsg[3];
byte current = 0x7F;
volatile int photoValue = 0;
int rotationCount = 0;

float distance_right = 0;
float distance_left = 0;
float distance_back = 0;

Servo servo;

volatile boolean update = false;
volatile int loop_timeout = 0;
volatile boolean got_ball = false;
boolean lastSeenLeft = true;

int PHOTO_THRESHOLD;

void setup() {
  int temp = 9999;
  
  pinMode(12, OUTPUT);
  analogWrite(12, 255);
  //###################################
  //## Initiate PinModes for Motors
  //###################################  
  pinMode(Motor_R_Dir,  OUTPUT);            
  pinMode(Motor_R_Step, OUTPUT);
  pinMode(Motor_R_MS1,  OUTPUT);            
  pinMode(Motor_R_MS2,  OUTPUT);
  pinMode(Motor_R_MS3,  OUTPUT);
  
  pinMode(Motor_L_Dir,  OUTPUT);            
  pinMode(Motor_L_Step, OUTPUT);
  pinMode(Motor_L_MS1,  OUTPUT);            
  pinMode(Motor_L_MS2,  OUTPUT);
  pinMode(Motor_L_MS3,  OUTPUT);
  
  pinMode(Motor_B_Dir,  OUTPUT);            
  pinMode(Motor_B_Step, OUTPUT);
  pinMode(Motor_B_MS1,  OUTPUT);            
  pinMode(Motor_B_MS2,  OUTPUT);
  pinMode(Motor_B_MS3,  OUTPUT);
  
  //###################################
  //## Initiate MS-Pins
  //###################################
  digitalWrite(Motor_R_MS1, HIGH);
  digitalWrite(Motor_R_MS2, LOW);
  digitalWrite(Motor_R_MS3, LOW);
  
  digitalWrite(Motor_L_MS1, HIGH);
  digitalWrite(Motor_L_MS2, LOW);
  digitalWrite(Motor_L_MS3, LOW);
  
  digitalWrite(Motor_B_MS1, HIGH);
  digitalWrite(Motor_B_MS2, LOW);
  digitalWrite(Motor_B_MS3, LOW);
  
  //###################################
  //## Initiate Sensor-Pins
  //###################################
  
  pinMode(RIGHT_TRIG, OUTPUT);
  pinMode(LEFT_TRIG, OUTPUT);
  pinMode(BACK_TRIG, OUTPUT);
  pinMode(RIGHT_ECHO, INPUT);
  pinMode(LEFT_ECHO, INPUT);
  pinMode(BACK_ECHO, INPUT);
  
  //###################################
  //## Initiate Servo
  //###################################
  servo.attach(SERVO_PIN);
  servo.write(SERVO_UP_POS);
  
  //###################################
  //## Initiate Interrupt Timer
  //###################################
  Timer3.initialize(100000);
  Timer3.attachInterrupt(callback); 
  
  //###################################
  //## Attach interrupt
  //###################################
  pinMode(COLLISION_PIN, OUTPUT);
  digitalWrite(COLLISION_PIN, LOW);
  attachInterrupt(1, collisionCallback, RISING);
    
  //###################################
  //## Start Accessory
  //###################################
  acc.powerOn();
  
  //###################################################################################
  //##### Initiating photoresistor
  //###################################################################################
  for(int i = 0; i <= FULL_ROTATION; i++) {
    int prRead = analogRead(PHOTO_PIN);
    if (prRead < temp)
      temp = prRead;
      
    turn(LEFT, HalfStep);
  }
  PHOTO_THRESHOLD = temp * .8;

}

/*
This method increments a counter every 0.1 second
*/
void callback(){
  loop_timeout++;
  update = true;
}

void collisionCallback() {
  /*
  In here one can add algorithms for collision avoidance
  */
  
  digitalWrite(COLLISION_PIN, LOW);
}

/*
Stes two motors
*/
void step2(int Motor_R, int Motor_L) {
  digitalWrite(Motor_R, HIGH);
  digitalWrite(Motor_L, HIGH);
  delay(1);
  digitalWrite(Motor_R, LOW);
  digitalWrite(Motor_L, LOW);
  delay(1);
}
/*
Steps all motors
*/
void step3(int Motor_R, int Motor_L, int Motor_B) {
  digitalWrite(Motor_R, HIGH);
  digitalWrite(Motor_L, HIGH);
  digitalWrite(Motor_B, HIGH);
  delay(1);
  digitalWrite(Motor_R, LOW);
  digitalWrite(Motor_L, LOW);
  digitalWrite(Motor_B, LOW);
  delay(1);
}

/*
Sets the desired speed for a specific motor. Can set a lower
speed if turning is required.
*/
void setStepRes(int motor, int res, boolean lower) {
  int MS1,MS2,MS3;
  
  switch(motor) {
  case Motor_Right:
    MS1 = Motor_R_MS1;
    MS2 = Motor_R_MS2;
    MS3 = Motor_R_MS3;
    break;
  case Motor_Left:
    MS1 = Motor_L_MS1;
    MS2 = Motor_L_MS2;
    MS3 = Motor_L_MS3;
    break;
  case Motor_Back:
    MS1 = Motor_B_MS1;
    MS2 = Motor_B_MS2;
    MS3 = Motor_B_MS3;
    break;
  }
  
  switch(res) {
  case FullStep:
    if (!lower)
      digitalWrite(MS1, LOW);
    else
      digitalWrite(MS1, HIGH);
    digitalWrite(MS2, LOW);
    digitalWrite(MS3, LOW);
    break;
  case HalfStep:
    if (!lower) {
      digitalWrite(MS1, HIGH);
      digitalWrite(MS2, LOW);
    } else {
      digitalWrite(MS1, LOW);
      digitalWrite(MS2, HIGH);
    }
    digitalWrite(MS3, LOW);
    break;
  case QuarterStep:
    if (!lower)
      digitalWrite(MS1, LOW);
    else
      digitalWrite(MS1, HIGH);
    digitalWrite(MS2, HIGH);
    digitalWrite(MS3, LOW);
    break;
  case EightStep:
    digitalWrite(MS1, HIGH);
    digitalWrite(MS2, HIGH);
    if (!lower)
      digitalWrite(MS3, LOW);
    else
      digitalWrite(MS1, HIGH);
    break;
  case SixteenthStep:
    digitalWrite(MS1, HIGH);
    digitalWrite(MS2, HIGH);
    digitalWrite(MS3, HIGH);
    break;
  }
}

/*
Moves robot forward. Can be set to turn.
*/
void moveForward(int res, boolean turn, boolean left) {
  digitalWrite(Motor_R_Dir, FORWARD);
  digitalWrite(Motor_L_Dir, !FORWARD);
  if (left) {
    digitalWrite(Motor_B_Dir, FORWARD);
    setStepRes(1, res, false);
    setStepRes(2, res, true);
  }
  else {
    digitalWrite(Motor_B_Dir, !FORWARD);
    setStepRes(1, res, true);
    setStepRes(2, res, false);
  }
  
  setStepRes(3, EightStep, false);
  
  if (turn)
    step3(Motor_R_Step, Motor_L_Step, Motor_B_Step);
  else
    setStepRes(1, res, false);
    setStepRes(2, res, false);
    step2(Motor_R_Step, Motor_L_Step);
}

/*
Move robot straight backwards
*/
void moveBackward(int res) {
  digitalWrite(Motor_R_Dir, BACKWARD);
  digitalWrite(Motor_L_Dir, !BACKWARD);
  setStepRes(1, res, false);
  setStepRes(2, res, false);

  step2(Motor_R_Step, Motor_L_Step);
}

/*
Turn robot either right or left at given speed
*/
void turn(int dir, int res){
  
  digitalWrite(Motor_R_Dir, dir);
  digitalWrite(Motor_L_Dir, dir);
  digitalWrite(Motor_B_Dir, dir);
  setStepRes(1, res, false);
  setStepRes(2, res, false);
  setStepRes(3, res, false);
  
  step3(Motor_R_Step, Motor_L_Step, Motor_B_Step);
}

/*
Measure distance of a specific ultrasonic sensor
*/
float measureDistance(int trig, int echo){
  float duration,temp;
    digitalWrite(trig, LOW);  // Added this line
    delayMicroseconds(2); // Added this line
    digitalWrite(trig, HIGH);
    delayMicroseconds(10); // Added this line
    digitalWrite(trig, LOW);
    duration = pulseIn(echo, HIGH);
    temp = (duration/2) / 29.1;
    if(temp < 3)
      temp = 3;
    else if(temp > 200)
      temp = 200;
      
    return temp;
}
/*
Reads instructions from the USB-connection and sends corresponding control signals to the compontens
Then sends what object to locate to the Android
*/
void loop() {
  if (acc.isConnected()) {
    
    int len = acc.read(rcvmsg, sizeof(rcvmsg), 1);
    if (update) {
      /*
      Read all the sensors every 0.1 seconds
      */
      photoValue = analogRead(PHOTO_PIN);
      distance_right = measureDistance(RIGHT_TRIG, RIGHT_ECHO);
      distance_left = measureDistance(LEFT_TRIG, LEFT_ECHO);
      distance_back = measureDistance(BACK_TRIG, BACK_ECHO);
      if ((distance_right <= COLLISION_THRESHOLD) || (distance_left <= COLLISION_THRESHOLD) || (distance_back <= COLLISION_THRESHOLD))
        digitalWrite(COLLISION_PIN, HIGH);
      
      update = false;
    }
    
    if (len > 0) {
      if (rcvmsg[0] == COMMAND_TEXT) {
        if (rcvmsg[1] == TARGET_DEFAULT){
          switch(rcvmsg[2]){
           
           case BIT_LOCATE_OBJECT:
             rotationCount++;
             if(rotationCount == FULL_ROTATION*2){
               for(int i = 0; i < FULL_ROTATION/2; i++){
                 turn(RIGHT, rcvmsg[3]);
                 delay(LOOP_DELAY);
               }
               for(int j = 0; j < 1600; j++){
                 moveForward(HalfStep, false, false);
                 delay(LOOP_DELAY);
               }
               rotationCount = 0;
             }
             if(lastSeenLeft)
               turn(LEFT, rcvmsg[3]);
             else
               turn(RIGHT, rcvmsg[3]);
             break;
             
           case BIT_OBJECT_CENTER:
             rotationCount = 0;
             moveForward(rcvmsg[3], false, false);
             break;
           
           case BIT_OBJECT_CENTER_LEFT:
             rotationCount = 0;
             moveForward(rcvmsg[3], true, true);
             break;
             
           case BIT_OBJECT_CENTER_RIGHT:
             rotationCount = 0;
             moveForward(rcvmsg[3], true, false);
             break;
           
           case BIT_OBJECT_RIGHT:
             rotationCount = 0;
             turn(RIGHT, rcvmsg[3]);
             lastSeenLeft = false;
             break;
           
           case BIT_OBJECT_LEFT:
             rotationCount = 0;
             turn(LEFT, rcvmsg[3]);
             lastSeenLeft = true;
             break;
             
           case BIT_SHOOT_BALL:
             rotationCount = 0;
             servo.write(SERVO_SHOOT);
             delay(200);
             servo.write(SERVO_UP_POS);
             //delay(3000);
             got_ball = false;
             break;
           
           case BIT_REVERSE:
             loop_timeout = 0;
             while (loop_timeout <= 10){
               moveBackward(rcvmsg[3]);
               delay(LOOP_DELAY);
             }
             break;
             
           case OBJECT_BALL_GRAB:
             loop_timeout = 0;
             int temp_timeout = 0;
             
             while(loop_timeout <= 15 && got_ball == false) {
                 moveForward(rcvmsg[3], false, false);
                 delay(LOOP_DELAY);
                 if (temp_timeout != loop_timeout) {
                   photoValue = analogRead(PHOTO_PIN);
                   temp_timeout = loop_timeout;
                   
                 }
                 
                 if (photoValue <= PHOTO_THRESHOLD)
                   got_ball = true;
             }
             if (!got_ball) {
               loop_timeout = 0;
               while(loop_timeout <= 5){
                 moveBackward(rcvmsg[3]);
                 delay(LOOP_DELAY);
               }
             }
             break;
           
          } 
         }
        }
      } 
    sntmsg[0] = COMMAND_TEXT;
    sntmsg[1] = TARGET_DEFAULT;
    
    
    if (got_ball) {
      if(current != SEARCH_GOAL){
        sntmsg[2] = SEARCH_GOAL;
        current =  SEARCH_GOAL;
        servo.write(SERVO_DOWN_POS);
      }
    }
    else {
      
      if (current != SEARCH_BALL){
        sntmsg[2] = SEARCH_BALL;
        current =  SEARCH_BALL;
      }
    }
    
    acc.write(sntmsg, 3);
  
 }
 
}
