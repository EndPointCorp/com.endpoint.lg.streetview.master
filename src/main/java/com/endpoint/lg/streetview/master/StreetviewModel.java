/*
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

import com.endpoint.lg.support.domain.streetview.StreetviewLink;

import com.endpoint.lg.support.domain.streetview.StreetviewLinks;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;

/**
 * A model representing the Street View state.
 * 
 * <p>
 * The model keeps track of whether or not its links are up-to-date. If the pano
 * is changed, the links are marked dirty and remain dirty until set. This helps
 * prevent navigation to stale links.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewModel {
  private StreetviewPano pano;
  private StreetviewPov pov;
  private StreetviewLinks links;

  private boolean linksDirty;

  public StreetviewPano getPano() {
    return pano;
  }

  /**
   * Set the current panorama
   * 
   * @param pano
   *          the pano
   * @return true if the pano changed
   */
  public boolean setPano(StreetviewPano pano) {
    if (this.pano == null || !this.pano.equals(pano)) {
      this.pano = pano;
      linksDirty = true;
      return true;
    }

    return false;
  }

  public StreetviewPov getPov() {
    return pov;
  }

  public void setPov(StreetviewPov pov) {
    this.pov = pov;
  }

  public StreetviewLinks getLinks() {
    return links;
  }

  public void setLinks(StreetviewLinks links) {
    this.links = links;
    linksDirty = false;
  }

  public StreetviewModel() {
    linksDirty = true;
    pov = new StreetviewPov(0, 0);
  }

  /**
   * Move to a neighboring panorama nearest to the given direction.
   * 
   * @param heading
   *          direction to move
   * @return true if the pano changed
   */
  public boolean moveToward(double heading) {
    if (linksDirty)
      return false; // this also prevents reading null links

    StreetviewLink nearest = links.getNearestLink(heading);

    if (nearest != null) {
      return setPano(new StreetviewPano(nearest.getPano()));
    }

    return false;
  }

  /**
   * Move to a neighboring panorama nearest the current heading.
   * 
   * @return true if the pano changed
   */
  public boolean moveForward() {
    if (pov != null)
      return moveToward(pov.getHeading());

    return false;
  }

  /**
   * Move to a neighboring panorama farthest from the current heading.
   * 
   * @return true if the pano changed
   */
  public boolean moveBackward() {
    if (pov != null)
      return moveToward(pov.getHeading() - 180);

    return false;
  }
}
