bnd-platform
============

Using OSGi and having trouble to get all the dependencies as proper OSGi bundles?
Even worse, sometimes you need to adapt bundles due to class loading issues or make an annoying dependency optional?

*bnd-platform* can help you solve that problem - it builds OSGi bundles and even Eclipse Update Sites from existing JARs, for instance retrieved from Maven repositories together with transitive dependencies. If needed you can adapt the creation of bundles via general or individual configuration options.
*bnd-platform* is a [Gradle](http://www.gradle.org/) plugin and uses [bnd](http://www.aqute.biz/Bnd/Bnd) to create bundles and [Eclipse](http://www.eclipse.org/) for the creation of p2 repositories.

For a quick start, check out the [sample project on GitHub](https://github.com/stempler/bnd-platform-sample).

License
-------

This software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
