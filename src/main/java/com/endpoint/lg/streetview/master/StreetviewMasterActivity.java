/*
 * Copyright (C) 2015 End Point Corporation
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.endpoint.lg.streetview.master;

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import interactivespaces.util.data.json.JsonNavigator;

import com.endpoint.lg.support.evdev.InputKeyEvent;

import com.endpoint.lg.support.message.RosMessageHandler;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;
import com.endpoint.lg.support.message.RosMessageHandlers;

import java.util.Map;

import com.endpoint.lg.support.evdev.InputAbsState;
import com.endpoint.lg.support.evdev.InputEventCodes;
import com.endpoint.lg.support.domain.streetview.StreetviewLinks;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;

/**
 * An activity responsible for authoritative Street View state, input handling,
 * and persistence.
 * 
 * <p>
 * Activity states:
 * 
 * <p>
 * RUNNING : State is receiving updates and responding to refresh requests.
 * 
 * <p>
 * ACTIVE : Input events are being handled.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewMasterActivity extends BaseRoutableRosActivity {

  /**
   * Coefficient of input event value to POV translation.
   */
  public static final double INPUT_SENSITIVITY = 0.0032;

  /**
   * How much "momentum" on a controller is needed to move forward or backward.
   */
  public static final int INPUT_MOVEMENT_COUNT = 10;

  /**
   * Controller forward/backward axes must exceed this value for movement (after
   * sensitivity).
   */
  public static final double INPUT_MOVEMENT_THRESHOLD = 1.0;

  /**
   * After axial movement, wait this many milliseconds before moving again.
   */
  public static final int INPUT_MOVEMENT_COOLDOWN = 250;

  private StreetviewModel model;

  private RosMessageHandlers rosHandlers;

  long lastMoveTime;
  int movementCounter;

  /**
   * Initialize movement state.
   */
  private void initMovement() {
    lastMoveTime = System.currentTimeMillis();
    movementCounter = 0;
  }

  /**
   * Sends incoming Ros messages to the Ros message handlers.
   */
  @Override
  public void onNewInputJson(String channel, Map<String, Object> message) {
    rosHandlers.handleMessage(channel, message);
  }

  /**
   * Broadcast the current point of view.
   */
  private void broadcastPov() {
    sendOutputJsonBuilder(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV, model.getPov()
        .getJsonBuilder());
  }

  /**
   * Broadcast the current panorama.
   */
  private void broadcastPano() {
    sendOutputJsonBuilder(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO, model.getPano()
        .getJsonBuilder());

    initMovement(); // reset movement state on pano change
  }

  /**
   * Initialize state, register message handlers.
   */
  @Override
  public void onActivitySetup() {
    model = new StreetviewModel();

    initMovement();

    rosHandlers = new RosMessageHandlers(getLog());

    // handles link updates from the browser
    rosHandlers.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_LINKS,
        new RosMessageHandler() {
          public void handleMessage(JsonNavigator json) {
            model.setLinks(new StreetviewLinks(json));
          }
        });

    // handle external pov updates
    rosHandlers.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV,
        new RosMessageHandler() {
          public void handleMessage(JsonNavigator json) {
            model.setPov(new StreetviewPov(json));
          }
        });

    // handle external pano updates
    rosHandlers.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO,
        new RosMessageHandler() {
          public void handleMessage(JsonNavigator json) {
            model.setPano(new StreetviewPano(json));
          }
        });

    // handle refresh requests
    rosHandlers.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH,
        new RosMessageHandler() {
          public void handleMessage(JsonNavigator json) {
            if (model.getPano() != null)
              broadcastPano();
            if (model.getPov() != null)
              broadcastPov();
          }
        });

    // handle button events, if activated
    rosHandlers.registerHandler("EV_KEY", new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        if (isActivated())
          onRosKeyEvent(new InputKeyEvent(json));
      }
    });

    // handle absolute axis state changes, if activated
    rosHandlers.registerHandler("EV_ABS", new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        if (isActivated())
          onRosAbsStateChange(new InputAbsState(json));
      }
    });
  }

  /**
   * Handle an EV_KEY event.
   * 
   * @param event
   *          the button event
   */
  private void onRosKeyEvent(InputKeyEvent event) {
    if (event.getValue() > 0) {
      if (event.getCode() == InputEventCodes.BTN_1 && model.moveForward()) {
        broadcastPano();
      }

      if (event.getCode() == InputEventCodes.BTN_0 && model.moveBackward()) {
        broadcastPano();
      }
    }
  }

  /**
   * Handle an EV_ABS state update.
   * 
   * @param state
   *          the axis state
   */
  private void onRosAbsStateChange(InputAbsState state) {
    double yaw = state.getValue(InputEventCodes.ABS_RZ) * INPUT_SENSITIVITY;

    if (yaw != 0) {
      model.getPov().translate(yaw, 0);

      broadcastPov();
    }

    long currentTime = System.currentTimeMillis();

    // movement can be either forwards or backwards, depending on whether the
    // SpaceNav is moved+tilted forwards or backwards.
    // TODO: Movement in all directions.
    double movement =
        -INPUT_SENSITIVITY
            * ((state.getValue(InputEventCodes.ABS_Y) + state.getValue(InputEventCodes.ABS_RX)));

    if (movement > INPUT_MOVEMENT_THRESHOLD) {
      movementCounter++;
    } else if (movement < -INPUT_MOVEMENT_THRESHOLD) {
      movementCounter--;
    } else {
      movementCounter = 0;
    }

    if ((currentTime - lastMoveTime) < INPUT_MOVEMENT_COOLDOWN) {
      movementCounter = 0;
    } else if (movementCounter > INPUT_MOVEMENT_COUNT && model.moveForward()) {
      broadcastPano();
    } else if (movementCounter < -INPUT_MOVEMENT_COUNT && model.moveBackward()) {
      broadcastPano();
    }
  }

  /**
   * Re-initialize movement state on activation.
   */
  @Override
  public void onActivityActivate() {
    initMovement();
  }
}