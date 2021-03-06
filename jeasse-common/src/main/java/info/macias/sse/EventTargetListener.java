/*
Copyright 2016 - Mario Macias Lloret

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package info.macias.sse;

/**
 * Listener which receives notifications for entering / leaving EventTargets
 * @author <a href="http://github.com/arkanovicz">Claude Brisson</a>
 */
public interface EventTargetListener {

    /**
     * A new subscriber just registered
     *
     * @param eventTarget The new subscriber
     */
    public void subscriberJoined(EventTarget eventTarget);

    /**
     * A subscriber just left
     *
     * @param eventTarget The new subscriber
     */
    public void subscriberLeft(EventTarget eventTarget);

}
