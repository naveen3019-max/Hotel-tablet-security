import React from 'react';

interface LiveIndicatorProps {
  status: 'connected' | 'connecting' | 'disconnected';
}

export default function LiveIndicator({ status }: LiveIndicatorProps) {
  if (status === 'connected') {
    return (
      <div className="flex items-center text-green-500 font-bold ml-4">
        <span className="w-3 h-3 bg-green-500 rounded-full mr-2 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.8)]"></span>
        LIVE
      </div>
    );
  }
  
  if (status === 'connecting') {
    return (
      <div className="flex items-center text-yellow-500 font-bold ml-4">
        <span className="w-3 h-3 bg-yellow-500 rounded-full mr-2"></span>
        RECONNECTING
      </div>
    );
  }

  return (
    <div className="flex items-center text-red-500 font-bold ml-4">
      <span className="w-3 h-3 bg-red-500 rounded-full mr-2"></span>
      POLLING MODE
    </div>
  );
}
