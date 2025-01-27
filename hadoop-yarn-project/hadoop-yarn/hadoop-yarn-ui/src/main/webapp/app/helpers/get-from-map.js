import Ember from 'ember';


function getNestedValue(obj, path) {
  return path.split('.').reduce((acc, key) => {
    if (acc === null || acc === undefined) {
      return undefined;
    }

    // Handle array indexing within the partition map
    const arrayMatch = key.match(/(\w+)\[(\d+)\]/);
    if (arrayMatch) {
      const arrayKey = arrayMatch[1];
      const arrayIndex = parseInt(arrayMatch[2], 10);
      return acc[arrayKey] && acc[arrayKey][arrayIndex];
    }

    return acc[key];
  }, obj);
}

export function getFromMap(params, hash) {
  //  Extract map values based on the key provided and the path to the nested value
  /*
  Example:
  XPATH from the metrics: /scheduler/schedulerInfo/capacities/queueCapacitiesByPartition[3]/configuredMinResource/resourceInformations/resourceInformation[2]/maximumAllocation
  The partition map is: queueCapacitiesByPartition and accessed as "partitionMap" from the code defined in the models/yarn-queue/capacity-queue.js
  The supplied hash.map is partitionMap
  The supplied key is the partition name (nodelabel), e.g. "customPartition"
  The parameter is "configuredMinResource/resourceInformations/resourceInformation[2]/maximumAllocation"
  The returned value is the value of the maximumAllocation, which in this case will be the number of vCores present.
  */
  return getNestedValue(hash.map[hash.key], hash.parameter);
}

export default Ember.Helper.helper(getFromMap);