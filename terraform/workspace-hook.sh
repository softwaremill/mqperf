#!/bin/bash

terraform workspace select "$1" 2>/dev/null || terraform workspace new "$1"