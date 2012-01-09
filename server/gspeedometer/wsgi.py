# Copyright 2012 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#!/usr/bin/python2.4
#
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Replacement WSGIApplication that supports Routes.

This code facilitates the use of Routes to invoke controllers based
on URLs and is based on code from an entry in the AppEngine cookbook,
http://appengine-cookbook.appspot.com/recipe/match-webapp-urls-using-routes.
"""

__author__ = 'qfang@google.com (Qinming Fang)'

import logging
import sys

from google.appengine.ext.webapp import Request
from google.appengine.ext.webapp import Response
from gspeedometer.helpers import error


_CONTROLLERS_MODULE_PREFIX = 'gspeedometer.controllers'


class WSGIApplication(object):
  """Wraps a set of webapp RequestHandlers in a WSGI-compatible application.

  This is based on webapp's WSGIApplication by Google, but uses Routes library
  (http://routes.groovie.org/) to match URLs.
  """

  def __init__(self, mapper, debug=False):
    """Initializes this application with the given URL mapping.

    Args:
      mapper: a routes.mapper.Mapper instance.
      debug: if true, we send Python stack traces to the browser on errors.
    """
    self.mapper = mapper
    self.__debug = debug
    WSGIApplication.active_instance = self
    self.current_request_args = ()

  def __call__(self, environ, start_response):
    """Called by WSGI when a request comes in."""
    request = Request(environ)
    response = Response()
    WSGIApplication.active_instance = self

    # Match the path against registered routes.
    kargs = self.mapper.match(request.path)
    if kargs is None:
      raise TypeError('No routes match. Provide a fallback to avoid this.')

    # Extract the module and controller names from the route.
    try:
      module_name, class_name = kargs['controller'].split(':', 1)
    except (KeyError, ValueError):
      module_name = kargs['controller']
      class_name = module_name
    del kargs['controller']
    module_name = _CONTROLLERS_MODULE_PREFIX + '.' + module_name

    # Initialize matched controller from given module.
    try:
      __import__(module_name)
      module = sys.modules[module_name]
      controller = getattr(module, class_name)()
      controller.initialize(request, response)
    except (ImportError, AttributeError):
      logging.exception('Could not import controller %s:%s',
                        module_name, class_name)
      raise ImportError('Controller %s from module %s could not be initialized.'
                        % (class_name, module_name))

    # Use the action set in the route, or the HTTP method.
    if 'action' in kargs:
      action = kargs['action']
      del kargs['action']
    else:
      action = environ['REQUEST_METHOD'].lower()
      if action not in [
          'get', 'post', 'head', 'options', 'put', 'delete', 'trace']:
        action = None

    if controller and action:
      try:
        # Execute the requested action, passing the route dictionary as
        # named parameters.
        getattr(controller, action)(**kargs)
      except error.AccessDenied, acl_e:
        logging.exception(acl_e)
        response.set_status(404)
      except Exception, e:
        # We want to catch any exception thrown by the controller and
        # pass it on to the controller's own exception handler.
        controller.handle_exception(e, self.__debug)

      response.wsgi_write(start_response)
      return ['']
    else:
      response.set_status(404)
